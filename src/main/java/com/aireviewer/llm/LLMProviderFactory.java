package com.aireviewer.llm;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Factory</b> pattern (simple factory) — turns a configuration string into a fully assembled,
 * production-ready {@link LLMProvider}.
 *
 * <p>Problem: choosing a provider is a configuration decision, but constructing one is a
 * three-object job — pick the adapter, wrap it in {@link ResilientLLMProvider}, give that a
 * fallback. If every call site did this, the choice of vendor would be hardcoded in each of them
 * ({@code new MistralLLMProvider(...)} scattered across the composition root, tests and tools),
 * and "swap the provider" would stop being a configuration change. Worse, one call site would
 * eventually forget the resilience wrapper and produce a provider with no safety net, which is the
 * kind of omission nobody notices until the API has an outage mid-demo.
 *
 * <p>This factory is therefore the only place in the application that names a concrete provider
 * class, and its product is <b>always</b> wrapped in resilience — including the mock (see
 * {@link #create}). Adding a fourth provider means one new branch here plus one adapter class, and
 * nothing else changes, which is the extension recipe CLAUDE.md §7 promises.
 *
 * <p>A simple factory, not an Abstract Factory: there is one product type and one selection axis,
 * so a switch over a normalized string is the whole job. Anything more elaborate would be pattern
 * for its own sake.
 *
 * <p>It takes plain parameters rather than reading settings itself: {@code llm} stays self-contained
 * and testable with no dependency on the {@code configuration} package, and the composition root
 * decides where values come from (environment variables, a local properties file, defaults).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * LLMProvider provider = LLMProviderFactory.create(
 *         LLMProviderFactory.KIND_MISTRAL,   // from configuration
 *         System.getenv("MISTRAL_API_KEY"),  // resolved by the composition root, never here
 *         null,                              // model: null or blank uses the provider default
 *         null, null,                        // local server settings, unused for this kind
 *         3, Duration.ofSeconds(2));         // resilience: attempts and delay
 * }</pre>
 *
 * <p>The full handoff example for a caller — building a request, sending it, validating the answer
 * — is in this package's {@code package-info.java}. Read that before using this package.
 */
public final class LLMProviderFactory {

    private static final Logger LOG = LoggerFactory.getLogger(LLMProviderFactory.class);

    /** Offline, deterministic provider. The default for development and every test. */
    public static final String KIND_MOCK = "mock";

    /** Mistral's hosted API. Requires an API key. */
    public static final String KIND_MISTRAL = "mistral";

    /** A local OpenAI-compatible server (LM Studio). Requires no API key. */
    public static final String KIND_LOCAL = "local";

    private LLMProviderFactory() {
        // Static factory; never instantiated.
    }

    /**
     * Builds the configured provider, wrapped in resilience with a mock fallback.
     *
     * <p>Every product is wrapped, <b>including {@link #KIND_MOCK}</b>. Wrapping the mock is
     * strictly unnecessary — it cannot fail — but it keeps one invariant instead of a special case:
     * whatever this factory returns has the same structure, so the retry-and-fallback path is
     * exercised during day-to-day development rather than for the first time in production. Since
     * {@link ResilientLLMProvider#describe()} delegates, the wrapper is invisible to callers, and
     * the cost is one object per run.
     *
     * <p>Only the Mistral API key has no safe default: a missing model name or local URL falls back
     * to that provider's documented default, but silently defaulting a credential would turn a
     * configuration mistake into a confusing HTTP 401 later.
     *
     * @param kind            which provider to build: {@link #KIND_MOCK}, {@link #KIND_MISTRAL} or
     *                        {@link #KIND_LOCAL}. Case and surrounding whitespace are ignored
     * @param mistralApiKey   Mistral API key; required for {@link #KIND_MISTRAL}, ignored otherwise
     * @param mistralModel    Mistral model name, or {@code null}/blank for
     *                        {@link MistralLLMProvider#DEFAULT_MODEL}
     * @param localBaseUrl    local server root, or {@code null}/blank for
     *                        {@link LocalLmStudioProvider#DEFAULT_BASE_URL}
     * @param localModel      local model name, or {@code null}/blank for
     *                        {@link LocalLmStudioProvider#DEFAULT_MODEL}
     * @param maxAttempts     attempts against the chosen provider before falling back, at least 1
     * @param retryDelay      wait between attempts; {@link Duration#ZERO} for none
     * @return a ready-to-use provider, never {@code null}
     * @throws IllegalArgumentException if the kind is unrecognized, or the configuration for the
     *                                  requested kind is unusable. Thrown at startup on purpose: a
     *                                  config mistake should stop the application immediately, not
     *                                  surface as a puzzling failure on the first evaluation
     */
    public static LLMProvider create(
            String kind,
            String mistralApiKey,
            String mistralModel,
            String localBaseUrl,
            String localModel,
            int maxAttempts,
            Duration retryDelay) {

        String normalizedKind = normalize(kind);
        LLMProvider primary = switch (normalizedKind) {
            case KIND_MOCK -> new MockLLMProvider();
            case KIND_MISTRAL -> mistral(mistralApiKey, mistralModel);
            case KIND_LOCAL -> local(localBaseUrl, localModel);
            default -> throw new IllegalArgumentException(
                    "Unknown LLM provider kind '" + kind + "'. Valid kinds are '" + KIND_MOCK
                            + "', '" + KIND_MISTRAL + "' and '" + KIND_LOCAL + "'.");
        };

        LLMProvider resilient =
                new ResilientLLMProvider(primary, new MockLLMProvider(), maxAttempts, retryDelay);

        LOG.info("LLM provider ready: {} (up to {} attempt(s), {} between retries, mock fallback)",
                resilient.describe(), maxAttempts, retryDelay);

        return resilient;
    }

    private static String normalize(String kind) {
        Objects.requireNonNull(kind, "kind");
        return kind.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Builds the Mistral adapter, adding context to whatever its constructor rejects.
     *
     * <p>The validation itself is not repeated here — {@link MistralLLMProvider} is the single
     * authority on what a usable Mistral configuration is. What this adds is the part the
     * constructor cannot know: which configured <em>kind</em> triggered the failure and what to do
     * about it. "Mistral API key must not be blank" is accurate; "you asked for kind 'mistral' but
     * no key was supplied, set MISTRAL_API_KEY or use kind 'mock'" is what actually unblocks a
     * teammate at 3am. A bare {@link NullPointerException} from a null key would be worse than
     * either.
     */
    private static LLMProvider mistral(String apiKey, String model) {
        try {
            return new MistralLLMProvider(
                    MistralLLMProvider.DEFAULT_BASE_URL,
                    orDefault(model, MistralLLMProvider.DEFAULT_MODEL),
                    apiKey == null ? "" : apiKey,
                    MistralLLMProvider.DEFAULT_TIMEOUT);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "LLM provider kind '" + KIND_MISTRAL + "' is not usable: " + invalid.getMessage()
                            + ". Supply the key (normally the MISTRAL_API_KEY environment variable, "
                            + "never a value committed to the repository), or use kind '" + KIND_MOCK
                            + "' to run entirely offline.",
                    invalid);
        }
    }

    private static LLMProvider local(String baseUrl, String model) {
        try {
            return new LocalLmStudioProvider(
                    orDefault(baseUrl, LocalLmStudioProvider.DEFAULT_BASE_URL),
                    orDefault(model, LocalLmStudioProvider.DEFAULT_MODEL),
                    LocalLmStudioProvider.DEFAULT_TIMEOUT);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "LLM provider kind '" + KIND_LOCAL + "' is not usable: " + invalid.getMessage()
                            + ". Check the local server URL (default "
                            + LocalLmStudioProvider.DEFAULT_BASE_URL + ") and that LM Studio is "
                            + "running, or use kind '" + KIND_MOCK + "'.",
                    invalid);
        }
    }

    /** Treats blank as absent, so an empty environment variable behaves like an unset one. */
    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
