package com.aireviewer.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * <b>Adapter</b> for Groq's hosted API, which serves open-weight models over the same
 * OpenAI-compatible endpoint as everything else in this package.
 *
 * <p>This class exists mostly as evidence: it is what "add a new LLM provider" actually costs here.
 * All the HTTP work, the two-message split, the status-to-{@link LLMException.Kind} mapping and the
 * response parsing come from {@link OpenAiCompatibleLLMProvider}; the only Groq-specific facts are
 * an endpoint, a default model and a mandatory key. Its practical value is a usable free tier, so a
 * teammate can run a real evaluation without setting up billing.
 *
 * <p>Configuration arrives through constructor parameters only, exactly as in
 * {@link MistralLLMProvider}: this package never reads {@code configuration} itself, and the key is
 * never logged nor put in an exception message.
 */
public final class GroqLLMProvider extends OpenAiCompatibleLLMProvider {

    /** Groq's OpenAI-compatible API root. */
    public static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";

    /**
     * Default model: the small production GPT-OSS model, verified against Groq's model list on
     * 2026-09-10 as available on the free/developer tier. Chosen because it is the cheapest
     * <em>production</em> chat model there and follows a JSON output contract well enough for
     * {@link LLMResponseValidator}. {@code openai/gpt-oss-120b} is the stronger sibling if scores
     * look shallow.
     *
     * <p>Model IDs on this platform expire: the Llama models this would have used a few months ago
     * ({@code llama-3.1-8b-instant}, {@code llama-3.3-70b-versatile}) became enterprise-only for
     * free and developer tiers on 2026-08-16. That is exactly why the model is a constructor
     * parameter — when this default dies, the fix is a configuration value, not a code change.
     * Groq's preview models are deliberately not used as defaults, since its own documentation says
     * they may be discontinued at short notice.
     */
    public static final String DEFAULT_MODEL = "openai/gpt-oss-20b";

    /**
     * Generous for Groq, which is unusually fast: this is a ceiling for a bad day rather than an
     * expected wait.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final Duration requestTimeout;

    /**
     * @param apiKey the Groq API key; required, and never logged
     */
    public GroqLLMProvider(String apiKey) {
        this(DEFAULT_BASE_URL, DEFAULT_MODEL, apiKey, DEFAULT_TIMEOUT);
    }

    /**
     * @param baseUrl        API root without a trailing slash
     * @param model          model name to request; see {@link #DEFAULT_MODEL} on why this is a
     *                       parameter and not a constant in the code path
     * @param apiKey         the API key; required and must not be blank
     * @param requestTimeout how long to wait for an answer
     */
    public GroqLLMProvider(String baseUrl, String model, String apiKey, Duration requestTimeout) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.model = Objects.requireNonNull(model, "model");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");

        if (apiKey.isBlank()) {
            // Fail at construction, not on the first evaluation: a missing key is a configuration
            // problem, and finding out mid-analysis wastes a run.
            throw new IllegalArgumentException("Groq API key must not be blank");
        }
        if (model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive but was " + requestTimeout);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    protected String baseUrl() {
        return baseUrl;
    }

    @Override
    protected String model() {
        return model;
    }

    @Override
    protected Optional<String> apiKey() {
        return Optional.of(apiKey);
    }

    @Override
    protected Duration requestTimeout() {
        return requestTimeout;
    }

    /** Names the vendor and the model, never the key. */
    @Override
    public String describe() {
        return "groq:" + model;
    }
}
