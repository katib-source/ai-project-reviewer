package com.aireviewer.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * <b>Adapter</b> for Mistral's hosted chat-completions API — the project's real, paid LLM.
 *
 * <p>All the HTTP work lives in {@link OpenAiCompatibleLLMProvider}; Mistral's API is that shape,
 * so this class only states what is specific to it: the endpoint root, a default model, and the
 * fact that a key is mandatory. That is the whole point of the base class — this file is what
 * "adding a provider" costs.
 *
 * <p>Configuration arrives through constructor parameters only. This package deliberately does not
 * read environment variables or a properties file: reaching into {@code configuration} from here
 * would couple every provider to a settings mechanism and make it untestable without one. The
 * composition root resolves the key and hands it over.
 *
 * <p>The key is never logged, never put in an exception message, and is scrubbed from quoted
 * response bodies by {@link OpenAiCompatibleLLMProvider#redactSecrets(String)}.
 */
public final class MistralLLMProvider extends OpenAiCompatibleLLMProvider {

    /** Mistral's API root. */
    public static final String DEFAULT_BASE_URL = "https://api.mistral.ai/v1";

    /** Small, fast and cheap enough to evaluate many criteria; good default for this project. */
    public static final String DEFAULT_MODEL = "mistral-small-latest";

    /**
     * Moderate by design: a hosted model normally answers an evaluation in a few seconds, so
     * waiting much longer mostly delays the retry that is going to fix it.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final Duration requestTimeout;

    /**
     * @param apiKey the Mistral API key; required, and never logged
     */
    public MistralLLMProvider(String apiKey) {
        this(DEFAULT_BASE_URL, DEFAULT_MODEL, apiKey, DEFAULT_TIMEOUT);
    }

    /**
     * @param baseUrl        API root without a trailing slash
     * @param model          model name to request
     * @param apiKey         the API key; required and must not be blank
     * @param requestTimeout how long to wait for an answer
     */
    public MistralLLMProvider(String baseUrl, String model, String apiKey, Duration requestTimeout) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.model = Objects.requireNonNull(model, "model");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");

        if (apiKey.isBlank()) {
            // Fail at construction, not on the first evaluation: a missing key is a configuration
            // problem, and finding out mid-analysis wastes a run.
            throw new IllegalArgumentException("Mistral API key must not be blank");
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
        return "mistral:" + model;
    }
}
