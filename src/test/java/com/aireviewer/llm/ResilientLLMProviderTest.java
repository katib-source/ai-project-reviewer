package com.aireviewer.llm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResilientLLMProviderTest {

    private static final Duration NO_DELAY = Duration.ZERO;

    private static LLMRequest request() {
        return new PromptBuilder().build(
                new PromptCriterion("coupling", "Coupling", "How loosely coupled the packages are.", 20),
                "public class Foo {}");
    }

    private static LLMException retryable(String message) {
        return new LLMException(LLMException.Kind.UNAVAILABLE, message);
    }

    private static LLMException nonRetryable(String message) {
        return new LLMException(LLMException.Kind.SCHEMA_VALIDATION, message);
    }

    @Test
    @DisplayName("a success on the first attempt is returned untouched")
    void returnsFirstSuccessAsIs() throws LLMException {
        LLMProvider delegate = mock(LLMProvider.class);
        LLMResponse expected = new LLMResponse("{\"score\": 15}", "primary-model", false);
        when(delegate.complete(any())).thenReturn(expected);
        LLMProvider fallback = mock(LLMProvider.class);

        LLMResponse actual =
                new ResilientLLMProvider(delegate, fallback, 3, NO_DELAY).complete(request());

        assertSame(expected, actual, "a successful response must not be rebuilt or re-flagged");
        assertFalse(actual.fromFallback());
        verify(delegate, times(1)).complete(any());
        verify(fallback, never()).complete(any());
    }

    @Test
    @DisplayName("fails twice then succeeds: returns the success after exactly 3 delegate calls")
    void retriesUntilSuccess() throws LLMException {
        LLMProvider delegate = mock(LLMProvider.class);
        LLMResponse success = new LLMResponse("{\"score\": 12}", "primary-model", false);
        when(delegate.complete(any()))
                .thenThrow(retryable("timeout 1"))
                .thenThrow(retryable("timeout 2"))
                .thenReturn(success);

        LLMResponse actual =
                new ResilientLLMProvider(delegate, null, 3, NO_DELAY).complete(request());

        assertSame(success, actual);
        assertFalse(actual.fromFallback(), "the primary answered in the end, however late");
        verify(delegate, times(3)).complete(any());
    }

    /**
     * Counts calls on their way to a real provider. Used instead of a Mockito {@code spy} on
     * {@link MockLLMProvider}: spying on a concrete class needs bytecode instrumentation of the
     * whole type hierarchy, which breaks whenever the JDK is newer than Mockito's Byte Buddy.
     * An interface-level wrapper keeps these tests independent of the JDK version.
     */
    private static final class CountingProvider implements LLMProvider {
        private final LLMProvider delegate;
        private final AtomicInteger calls = new AtomicInteger();

        CountingProvider(LLMProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public LLMResponse complete(LLMRequest request) throws LLMException {
            calls.incrementAndGet();
            return delegate.complete(request);
        }

        @Override
        public String describe() {
            return delegate.describe();
        }

        int calls() {
            return calls.get();
        }
    }

    @Test
    @DisplayName("delegate always failing: falls back after exactly maxAttempts, forcing fromFallback")
    void fallsBackAfterExhaustingAttempts() throws LLMException {
        CountingProvider delegate = new CountingProvider(MockLLMProvider.alwaysFailing());
        MockLLMProvider fallback = new MockLLMProvider();
        LLMResponse fallbackAnswer = fallback.complete(request());

        LLMResponse actual =
                new ResilientLLMProvider(delegate, fallback, 4, NO_DELAY).complete(request());

        assertEquals(fallbackAnswer.content(), actual.content(), "the fallback's answer is returned");
        assertEquals("mock", actual.model());
        assertTrue(actual.fromFallback(),
                "the mock reports false about itself; only the decorator knows the primary failed");
        assertFalse(fallbackAnswer.fromFallback(), "and the fallback was left unchanged");
        assertEquals(4, delegate.calls());
    }

    @Test
    @DisplayName("delegate always failing with no fallback: the last exception propagates")
    void propagatesWhenNoFallbackConfigured() throws LLMException {
        CountingProvider delegate = new CountingProvider(MockLLMProvider.alwaysFailing());
        ResilientLLMProvider provider = new ResilientLLMProvider(delegate, null, 3, NO_DELAY);

        LLMException failure = assertThrows(LLMException.class, () -> provider.complete(request()));

        assertEquals(LLMException.Kind.UNAVAILABLE, failure.kind());
        assertEquals(0, failure.getSuppressed().length, "there was no fallback failure to attach");
        assertEquals(3, delegate.calls());
    }

    @Test
    @DisplayName("a non-retryable failure is not retried: one call, straight to the fallback")
    void doesNotRetryNonRetryableFailures() throws LLMException {
        LLMProvider delegate = mock(LLMProvider.class);
        when(delegate.complete(any())).thenThrow(nonRetryable("score was a string"));
        MockLLMProvider fallback = new MockLLMProvider();

        LLMResponse actual =
                new ResilientLLMProvider(delegate, fallback, 5, NO_DELAY).complete(request());

        assertTrue(actual.fromFallback());
        verify(delegate, times(1)).complete(any());
    }

    @Test
    @DisplayName("a non-retryable failure with no fallback propagates after one call")
    void propagatesNonRetryableFailureImmediately() throws LLMException {
        LLMProvider delegate = mock(LLMProvider.class);
        when(delegate.complete(any())).thenThrow(nonRetryable("missing score"));
        ResilientLLMProvider provider = new ResilientLLMProvider(delegate, null, 5, NO_DELAY);

        LLMException failure = assertThrows(LLMException.class, () -> provider.complete(request()));

        assertEquals(LLMException.Kind.SCHEMA_VALIDATION, failure.kind());
        verify(delegate, times(1)).complete(any());
    }

    @Test
    @DisplayName("fallback also fails: its exception propagates with the primary's suppressed")
    void attachesPrimaryFailureWhenFallbackFails() throws LLMException {
        LLMProvider delegate = mock(LLMProvider.class);
        LLMException primaryFailure = retryable("primary is down");
        when(delegate.complete(any())).thenThrow(primaryFailure);

        LLMProvider fallback = mock(LLMProvider.class);
        LLMException fallbackFailure = new LLMException(LLMException.Kind.HTTP_ERROR, "fallback said 500");
        when(fallback.complete(any())).thenThrow(fallbackFailure);

        ResilientLLMProvider provider = new ResilientLLMProvider(delegate, fallback, 2, NO_DELAY);

        LLMException thrown = assertThrows(LLMException.class, () -> provider.complete(request()));

        assertSame(fallbackFailure, thrown, "the caller sees the failure that ended the attempt");
        assertArrayEquals(new Throwable[] {primaryFailure}, thrown.getSuppressed(),
                "the primary failure is usually the interesting one when debugging");
        verify(delegate, times(2)).complete(any());
        verify(fallback, times(1)).complete(any());
    }

    @Test
    @DisplayName("maxAttempts = 1 means no retries at all")
    void singleAttemptDoesNotRetry() throws LLMException {
        LLMProvider delegate = mock(LLMProvider.class);
        when(delegate.complete(any())).thenThrow(retryable("down"));
        MockLLMProvider fallback = new MockLLMProvider();

        LLMResponse actual =
                new ResilientLLMProvider(delegate, fallback, 1, NO_DELAY).complete(request());

        assertTrue(actual.fromFallback());
        verify(delegate, times(1)).complete(any());
    }

    @Test
    @DisplayName("maxAttempts = 1 with no fallback propagates the first failure")
    void singleAttemptWithoutFallbackPropagates() throws LLMException {
        LLMProvider delegate = mock(LLMProvider.class);
        when(delegate.complete(any())).thenThrow(retryable("down"));
        ResilientLLMProvider provider = new ResilientLLMProvider(delegate, null, 1, NO_DELAY);

        assertThrows(LLMException.class, () -> provider.complete(request()));

        verify(delegate, times(1)).complete(any());
    }

    @Test
    @DisplayName("the configured delay is waited between attempts, and not after the last one")
    void waitsBetweenAttempts() throws LLMException {
        LLMProvider delegate = mock(LLMProvider.class);
        when(delegate.complete(any())).thenThrow(retryable("down"));
        ResilientLLMProvider provider =
                new ResilientLLMProvider(delegate, null, 3, Duration.ofMillis(30));

        long startedAt = System.nanoTime();
        assertThrows(LLMException.class, () -> provider.complete(request()));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // Three attempts means two waits, never a third after the final failure.
        assertTrue(elapsed.toMillis() >= 60, "expected at least two waits, took " + elapsed.toMillis() + "ms");
        assertTrue(elapsed.toMillis() < 500, "should not have waited after the last attempt");
        verify(delegate, times(3)).complete(any());
    }

    @Test
    @DisplayName("describe() reports the delegate, so wrapping does not rename the model")
    void describesTheDelegate() {
        LLMProvider delegate = mock(LLMProvider.class);
        when(delegate.describe()).thenReturn("mistral:mistral-small-latest");

        ResilientLLMProvider provider =
                new ResilientLLMProvider(delegate, new MockLLMProvider(), 3, NO_DELAY);

        assertEquals("mistral:mistral-small-latest", provider.describe());
    }

    @Test
    @DisplayName("decorators compose: a resilient provider can wrap another one")
    void composesWithItself() throws LLMException {
        LLMProvider inner = new ResilientLLMProvider(MockLLMProvider.alwaysFailing(), null, 2, NO_DELAY);
        MockLLMProvider fallback = new MockLLMProvider();

        LLMResponse actual = new ResilientLLMProvider(inner, fallback, 2, NO_DELAY).complete(request());

        assertTrue(actual.fromFallback());
        assertEquals("mock", actual.model());
        assertEquals("mock", inner.describe(), "the inner decorator still reports its own delegate");
    }

    @Test
    @DisplayName("an answer that reaches the validator is still a valid evaluation after fallback")
    void fallbackAnswerStillValidates() throws LLMException {
        ResilientLLMProvider provider =
                new ResilientLLMProvider(MockLLMProvider.alwaysFailing(), new MockLLMProvider(), 2, NO_DELAY);

        LLMResponse response = provider.complete(request());
        LLMEvaluation evaluation = new LLMResponseValidator().validate(response);

        assertEquals("coupling", evaluation.criterion().orElseThrow());
        assertTrue(response.fromFallback());
        assertTrue(evaluation.fromFallback(),
                "the flag must survive validation: this is what the report reads");
    }

    @Test
    @DisplayName("bad construction arguments are rejected loudly")
    void rejectsBadArguments() {
        LLMProvider delegate = mock(LLMProvider.class);

        assertThrows(NullPointerException.class,
                () -> new ResilientLLMProvider(null, null, 3, NO_DELAY));
        assertThrows(NullPointerException.class,
                () -> new ResilientLLMProvider(delegate, null, 3, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResilientLLMProvider(delegate, null, 0, NO_DELAY));
        assertThrows(IllegalArgumentException.class,
                () -> new ResilientLLMProvider(delegate, null, 3, Duration.ofMillis(-1)));
    }

    @Test
    @DisplayName("retryability comes from the failure kind, not from this class")
    void retryabilityLivesOnTheKind() {
        assertTrue(LLMException.Kind.TIMEOUT.isRetryable());
        assertTrue(LLMException.Kind.UNAVAILABLE.isRetryable());
        assertFalse(LLMException.Kind.SCHEMA_VALIDATION.isRetryable());
        assertFalse(LLMException.Kind.MALFORMED_RESPONSE.isRetryable());
        assertFalse(LLMException.Kind.EMPTY_RESPONSE.isRetryable());
        assertFalse(LLMException.Kind.HTTP_ERROR.isRetryable());
    }
}
