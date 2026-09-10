package com.aireviewer.llm;

import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Decorator</b> pattern — wraps any {@link LLMProvider} and adds retry and fallback around it.
 *
 * <p>Problem: an LLM endpoint is the least reliable part of this application. It times out, it
 * rate-limits, it goes down mid-analysis. Retry logic has to live somewhere, and the two obvious
 * places are both wrong. Putting it in each adapter duplicates it per vendor and tangles two
 * concerns — translating one API and surviving a flaky network — inside one class. Putting it in
 * the analysis engine would make the engine know that answers come from something unreliable over a
 * network, which is exactly the knowledge the {@link LLMProvider} abstraction exists to hide.
 *
 * <p>A decorator solves it because it <em>is</em> an {@link LLMProvider}: callers cannot tell they
 * are talking to one, each adapter stays a thin translation of its vendor's API, and resilience is
 * written once for providers that do not exist yet. It also composes — wrapping a
 * {@code ResilientLLMProvider} in another one is meaningless but harmless, and that property is
 * what makes the pattern the right choice rather than a base class every adapter must extend.
 *
 * <h2>What it does</h2>
 * <ol>
 *   <li>Calls the delegate. A success is returned untouched.</li>
 *   <li>On failure, asks {@link LLMException.Kind#isRetryable()} whether another attempt could
 *       help. If not, it stops at once rather than spending the remaining attempts on a failure
 *       that will repeat identically — a wrong-schema answer is not a network hiccup.</li>
 *   <li>If retryable and attempts remain, waits the configured delay and tries again.</li>
 *   <li>Once the delegate is out of attempts, calls the fallback provider once, if one is
 *       configured. A fallback answer is always marked {@code fromFallback = true}.</li>
 *   <li>With no fallback, the last delegate failure propagates unchanged.</li>
 * </ol>
 *
 * <p>Retryability is deliberately <em>not</em> decided here: it is a property of the failure, so it
 * lives on {@link LLMException.Kind}, and this class only reads it.
 *
 * <p>Only {@link LLMException} is handled. An unchecked exception from a provider is a bug in that
 * provider, not a transient fault, and retrying it three times before quietly substituting a
 * fallback answer would hide it — so it propagates untouched.
 *
 * <p>Stateless: attempt counters and failures are locals, so nothing is shared between calls and
 * the instance is as thread-safe as the providers it wraps, which {@link LLMProvider} requires them
 * to be.
 */
public final class ResilientLLMProvider implements LLMProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ResilientLLMProvider.class);

    private final LLMProvider delegate;

    /** May be {@code null}: running without a fallback is a valid configuration, not an error. */
    private final LLMProvider fallback;

    private final int maxAttempts;
    private final Duration retryDelay;

    /**
     * @param delegate    the provider to protect, never {@code null}
     * @param fallback    provider to try once after the delegate is exhausted, or {@code null} for
     *                    none. Typically a {@link MockLLMProvider} or a local model — something
     *                    that fails for different reasons than the primary, since a fallback
     *                    sharing the primary's failure mode buys nothing
     * @param maxAttempts total attempts against the delegate, including the first; at least 1
     * @param retryDelay  wait between attempts; {@link Duration#ZERO} disables waiting
     */
    public ResilientLLMProvider(
            LLMProvider delegate, LLMProvider fallback, int maxAttempts, Duration retryDelay) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.fallback = fallback;
        this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1 but was " + maxAttempts);
        }
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("retryDelay must not be negative but was " + retryDelay);
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public LLMResponse complete(LLMRequest request) throws LLMException {
        Objects.requireNonNull(request, "request");

        LLMException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.complete(request);
            } catch (LLMException failure) {
                lastFailure = failure;

                // Messages and kinds only: a request carries analyzed project content, which must
                // never reach the logs (CLAUDE.md §4).
                if (!failure.kind().isRetryable()) {
                    LOG.warn("Provider '{}' failed for criterion '{}' with non-retryable {}; "
                                    + "not retrying",
                            delegate.describe(), request.criterionId(), failure.kind());
                    break;
                }

                LOG.warn("Provider '{}' attempt {}/{} for criterion '{}' failed with {}",
                        delegate.describe(), attempt, maxAttempts, request.criterionId(), failure.kind());

                if (attempt == maxAttempts) {
                    break;
                }
                waitBeforeRetry(failure);
            }
        }

        return fallbackOrPropagate(request, lastFailure);
    }

    /**
     * Reports the delegate's identity, not its own.
     *
     * <p>{@link #describe()} answers "which engine produced this evaluation", and that is the
     * delegate — a decorator is not a different model, and stamping something like
     * {@code "resilient(mistral:...)"} into a report would describe our plumbing rather than the
     * thing being reported. The resilience configuration shows up where it is actually useful, in
     * the logs above; and when a fallback answers, the response itself carries the attribution via
     * {@link LLMResponse#model()} and {@link LLMResponse#fromFallback()}, which is per-answer and
     * therefore more accurate than anything this method could say. Delegating also keeps nesting
     * harmless: wrapping twice does not produce a doubled-up name.
     */
    @Override
    public String describe() {
        return delegate.describe();
    }

    /**
     * Waits between attempts, treating an interrupt as "the caller wants out".
     *
     * <p>On interruption the flag is restored and the last failure is thrown immediately: a
     * decorator that kept retrying, or even started a fallback call, would be doing new work for a
     * caller that has already asked to stop.
     */
    private void waitBeforeRetry(LLMException lastFailure) throws LLMException {
        if (retryDelay.isZero()) {
            return;
        }
        try {
            Thread.sleep(retryDelay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting to retry provider '{}'; giving up", delegate.describe());
            throw lastFailure;
        }
    }

    /**
     * Last resort: try the fallback once, or propagate.
     *
     * <p>The returned response is rebuilt with {@code fromFallback = true} whatever the fallback
     * itself reported. The fallback cannot know it is a fallback — it was simply called — so the
     * flag belongs to the only object that knows the primary failed, which is this one.
     * {@link MockLLMProvider} documents the same reasoning from the other side.
     */
    private LLMResponse fallbackOrPropagate(LLMRequest request, LLMException lastFailure)
            throws LLMException {
        assert lastFailure != null : "reached only after at least one delegate failure";

        if (fallback == null) {
            throw lastFailure;
        }

        LOG.warn("Provider '{}' exhausted for criterion '{}' ({}); falling back to '{}'",
                delegate.describe(), request.criterionId(), lastFailure.kind(), fallback.describe());

        try {
            LLMResponse response = fallback.complete(request);
            return new LLMResponse(response.content(), response.model(), true);
        } catch (LLMException fallbackFailure) {
            // Both failures matter when debugging: the fallback's is what the caller sees, but the
            // primary's is usually the interesting one.
            fallbackFailure.addSuppressed(lastFailure);
            LOG.warn("Fallback provider '{}' also failed for criterion '{}' with {}",
                    fallback.describe(), request.criterionId(), fallbackFailure.kind());
            throw fallbackFailure;
        }
    }
}
