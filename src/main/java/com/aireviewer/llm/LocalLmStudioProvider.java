package com.aireviewer.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * <b>Adapter</b> for a locally hosted model served by LM Studio, which exposes the same
 * OpenAI-compatible endpoint on {@code http://localhost:1234/v1} by default.
 *
 * <p>Its value here is not that it is a different vendor but that it is a different <em>kind</em>
 * of provider: no API key, no cost, no rate limit, no data leaving the machine — and slow. That
 * makes it both a demonstration that the {@link LLMProvider} abstraction really is
 * vendor-neutral (two providers whose failure modes have almost nothing in common sit behind one
 * interface) and a sensible fallback for {@link ResilientLLMProvider}, since it cannot fail for the
 * same reasons a hosted API does.
 *
 * <p>Like {@link MistralLLMProvider}, everything comes from constructor parameters; this package
 * never reads configuration itself.
 */
public final class LocalLmStudioProvider extends OpenAiCompatibleLLMProvider {

    /** LM Studio's default local server address. */
    public static final String DEFAULT_BASE_URL = "http://localhost:1234/v1";

    /**
     * LM Studio serves whatever model is loaded in the app and accepts a placeholder name, so this
     * default works without knowing which model the user picked.
     */
    public static final String DEFAULT_MODEL = "local-model";

    /**
     * Generous on purpose: local inference on a laptop CPU can take minutes for a long prompt,
     * where a hosted API would answer in seconds. Timing out early would turn a slow-but-working
     * setup into a stream of retries.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(3);

    private final String baseUrl;
    private final String model;
    private final Duration requestTimeout;

    /** Uses the defaults above — the common case for a developer running LM Studio locally. */
    public LocalLmStudioProvider() {
        this(DEFAULT_BASE_URL, DEFAULT_MODEL, DEFAULT_TIMEOUT);
    }

    /**
     * @param baseUrl        API root without a trailing slash
     * @param model          model name to request; LM Studio ignores it when one model is loaded
     * @param requestTimeout how long to wait for an answer
     */
    public LocalLmStudioProvider(String baseUrl, String model, Duration requestTimeout) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.model = Objects.requireNonNull(model, "model");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");

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

    /**
     * No credentials: a local server needs none, so no {@code Authorization} header is sent at all.
     * There is consequently no secret to redact for this provider.
     */
    @Override
    protected Optional<String> apiKey() {
        return Optional.empty();
    }

    @Override
    protected Duration requestTimeout() {
        return requestTimeout;
    }

    @Override
    public String describe() {
        return "lmstudio:" + model;
    }
}
