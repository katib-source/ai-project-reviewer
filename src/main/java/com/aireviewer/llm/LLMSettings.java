package com.aireviewer.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything this package needs to configure itself, in one immutable value.
 *
 * <p>It exists to keep {@code llm} self-contained. The obvious alternative — reading environment
 * variables here, or depending on the {@code configuration} package — would tie every provider to
 * one settings mechanism and make the whole package untestable without it. Instead the composition
 * root reads settings from wherever it likes and fills this record in, so this package never learns
 * where a value came from. It is deliberately not the {@code configuration} package's own type
 * either: that one will carry sandbox limits, report paths and Javalin's port, none of which belong
 * in a decision about talking to a model.
 *
 * <p>It replaced a seven-argument factory method. Positional arguments were the wrong shape for
 * this: {@code create(kind, key, model, baseUrl, model, attempts, delay)} has two adjacent
 * {@code String} pairs, so transposing a URL and a model name compiled cleanly and failed at
 * runtime. Naming each value at the call site removes that class of mistake, and adding a knob no
 * longer changes a signature every caller has to follow.
 *
 * <p>Credentials are per-vendor fields ({@code mistralApiKey}, {@code groqApiKey}) rather than one
 * shared {@code apiKey}, so switching kinds does not silently send one vendor's key to another and
 * a caller can keep both configured. That said, this is the shape's limit: a third hosted vendor
 * would make a fifth and sixth credential field, and the better answer then is one
 * {@code Map<String, VendorCredentials>} keyed by kind. Two vendors do not justify that machinery
 * yet.
 *
 * <p>Start from {@link #defaults()} and adjust with the {@code with*} methods — these are plain
 * copy-with-one-change helpers, not a Builder pattern; the record is small enough that a builder
 * class would be ceremony. All values are validated on construction, so an unusable configuration
 * fails at startup rather than on the first evaluation.
 *
 * @param kind            which provider to build: {@link LLMProviderFactory#KIND_MOCK},
 *                        {@link LLMProviderFactory#KIND_MISTRAL},
 *                        {@link LLMProviderFactory#KIND_GROQ} or
 *                        {@link LLMProviderFactory#KIND_LOCAL}
 * @param mistralApiKey   Mistral API key; required only for the {@code mistral} kind. Blank means
 *                        absent, so an unset environment variable behaves like no key at all
 * @param mistralModel    Mistral model name; blank uses {@link MistralLLMProvider#DEFAULT_MODEL}
 * @param groqApiKey      Groq API key; required only for the {@code groq} kind. Blank means absent
 * @param groqModel       Groq model name; blank uses {@link GroqLLMProvider#DEFAULT_MODEL}
 * @param localBaseUrl    local server root; blank uses {@link LocalLmStudioProvider#DEFAULT_BASE_URL}
 * @param localModel      local model name; blank uses {@link LocalLmStudioProvider#DEFAULT_MODEL}
 * @param maxAttempts     attempts against the chosen provider before falling back; at least 1
 * @param retryDelay      wait between attempts; {@link Duration#ZERO} for none
 * @param temperature     sampling temperature for evaluation prompts; low keeps scores
 *                        near-reproducible
 * @param maxOutputTokens cap on answer length, which also caps cost
 * @param providerTimeout how long to wait for one answer, or empty to keep each provider's own
 *                        default — 30 s for a hosted API against 3 minutes for local inference,
 *                        which is a genuine difference in kind rather than a value worth averaging
 */
public record LLMSettings(
        String kind,
        String mistralApiKey,
        String mistralModel,
        String groqApiKey,
        String groqModel,
        String localBaseUrl,
        String localModel,
        int maxAttempts,
        Duration retryDelay,
        double temperature,
        int maxOutputTokens,
        Optional<Duration> providerTimeout) {

    /** Offline by default: no key needed, nothing leaves the machine, every test can use it. */
    public static final String DEFAULT_KIND = LLMProviderFactory.KIND_MOCK;

    /** Three tries absorbs a transient blip without making a dead endpoint slow to give up on. */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /** Long enough for a rate limit window to move, short enough not to stall an analysis run. */
    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(2);

    /** Low but non-zero: evaluations should be near-reproducible without pinning one phrasing. */
    public static final double DEFAULT_TEMPERATURE = 0.2;

    /** Room for a JSON object with three short prose lists, and not much more. */
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 900;

    /** Highest temperature any OpenAI-compatible vendor accepts. */
    private static final double MAX_TEMPERATURE = 2.0;

    /** Normalizes absent values to blank and rejects anything unusable. */
    public LLMSettings {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(retryDelay, "retryDelay");
        Objects.requireNonNull(providerTimeout, "providerTimeout");

        // Null and blank mean the same thing — "not configured" — so they are stored the same way,
        // and no accessor here ever returns null.
        mistralApiKey = blankIfNull(mistralApiKey);
        mistralModel = blankIfNull(mistralModel);
        groqApiKey = blankIfNull(groqApiKey);
        groqModel = blankIfNull(groqModel);
        localBaseUrl = blankIfNull(localBaseUrl);
        localModel = blankIfNull(localModel);

        if (kind.isBlank()) {
            throw new IllegalArgumentException("kind must not be blank");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1 but was " + maxAttempts);
        }
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("retryDelay must not be negative but was " + retryDelay);
        }
        if (!Double.isFinite(temperature) || temperature < 0 || temperature > MAX_TEMPERATURE) {
            throw new IllegalArgumentException(
                    "temperature must be between 0 and " + MAX_TEMPERATURE + " but was " + temperature);
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException(
                    "maxOutputTokens must be at least 1 but was " + maxOutputTokens);
        }
        if (providerTimeout.isPresent()
                && (providerTimeout.get().isZero() || providerTimeout.get().isNegative())) {
            throw new IllegalArgumentException(
                    "providerTimeout must be positive but was " + providerTimeout.get());
        }
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    /**
     * The safe starting point: the offline mock provider, three attempts, and today's sampling
     * defaults. Running with these needs no key, no network and no local server.
     *
     * @return default settings, never {@code null}
     */
    public static LLMSettings defaults() {
        return new LLMSettings(
                DEFAULT_KIND, "", "", "", "", "", "",
                DEFAULT_MAX_ATTEMPTS, DEFAULT_RETRY_DELAY,
                DEFAULT_TEMPERATURE, DEFAULT_MAX_OUTPUT_TOKENS,
                Optional.empty());
    }

    /**
     * @param newKind the provider kind to build
     * @return a copy with that kind
     */
    public LLMSettings withKind(String newKind) {
        return new LLMSettings(newKind, mistralApiKey, mistralModel, groqApiKey, groqModel, localBaseUrl, localModel,
                maxAttempts, retryDelay, temperature, maxOutputTokens, providerTimeout);
    }

    /**
     * Selects the Mistral kind and its credentials in one step, since a key without the kind, or
     * the kind without a key, is never what a caller meant.
     *
     * @param apiKey Mistral API key
     * @param model  model name, or blank/{@code null} for the provider default
     * @return a copy configured for Mistral
     */
    public LLMSettings withMistral(String apiKey, String model) {
        return new LLMSettings(LLMProviderFactory.KIND_MISTRAL, apiKey, model, groqApiKey, groqModel,
                localBaseUrl, localModel,
                maxAttempts, retryDelay, temperature, maxOutputTokens, providerTimeout);
    }

    /**
     * Selects the Groq kind and its credentials, the same way {@link #withMistral} does.
     *
     * @param apiKey Groq API key
     * @param model  model name, or blank/{@code null} for the provider default
     * @return a copy configured for Groq
     */
    public LLMSettings withGroq(String apiKey, String model) {
        return new LLMSettings(LLMProviderFactory.KIND_GROQ, mistralApiKey, mistralModel, apiKey, model,
                localBaseUrl, localModel,
                maxAttempts, retryDelay, temperature, maxOutputTokens, providerTimeout);
    }

    /**
     * Selects the local kind and its server details.
     *
     * @param baseUrl server root, or blank/{@code null} for the provider default
     * @param model   model name, or blank/{@code null} for the provider default
     * @return a copy configured for a local server
     */
    public LLMSettings withLocal(String baseUrl, String model) {
        return new LLMSettings(LLMProviderFactory.KIND_LOCAL, mistralApiKey, mistralModel, groqApiKey, groqModel,
                baseUrl, model,
                maxAttempts, retryDelay, temperature, maxOutputTokens, providerTimeout);
    }

    /**
     * @param newMaxAttempts attempts before falling back
     * @param newRetryDelay  wait between attempts
     * @return a copy with those resilience settings
     */
    public LLMSettings withResilience(int newMaxAttempts, Duration newRetryDelay) {
        return new LLMSettings(kind, mistralApiKey, mistralModel, groqApiKey, groqModel, localBaseUrl, localModel,
                newMaxAttempts, newRetryDelay, temperature, maxOutputTokens, providerTimeout);
    }

    /**
     * @param newTemperature     sampling temperature
     * @param newMaxOutputTokens cap on answer length
     * @return a copy with those sampling settings
     */
    public LLMSettings withSampling(double newTemperature, int newMaxOutputTokens) {
        return new LLMSettings(kind, mistralApiKey, mistralModel, groqApiKey, groqModel, localBaseUrl, localModel,
                maxAttempts, retryDelay, newTemperature, newMaxOutputTokens, providerTimeout);
    }

    /**
     * @param timeout how long to wait for one answer, overriding the provider's own default
     * @return a copy with that timeout
     */
    public LLMSettings withProviderTimeout(Duration timeout) {
        return new LLMSettings(kind, mistralApiKey, mistralModel, groqApiKey, groqModel, localBaseUrl, localModel,
                maxAttempts, retryDelay, temperature, maxOutputTokens,
                Optional.of(Objects.requireNonNull(timeout, "timeout")));
    }

    /**
     * Deliberately hides the API key, so that logging a settings object — the sort of thing done
     * while debugging a configuration problem, which is exactly when a key is set — cannot leak the
     * credential (CLAUDE.md §4). Whether a key is present is still visible, because that is the
     * useful part.
     */
    @Override
    public String toString() {
        return "LLMSettings[kind=" + kind
                + ", mistralApiKey=" + (mistralApiKey.isBlank() ? "<absent>" : "<set>")
                + ", mistralModel=" + mistralModel
                + ", groqApiKey=" + (groqApiKey.isBlank() ? "<absent>" : "<set>")
                + ", groqModel=" + groqModel
                + ", localBaseUrl=" + localBaseUrl
                + ", localModel=" + localModel
                + ", maxAttempts=" + maxAttempts
                + ", retryDelay=" + retryDelay
                + ", temperature=" + temperature
                + ", maxOutputTokens=" + maxOutputTokens
                + ", providerTimeout=" + providerTimeout.map(Duration::toString).orElse("<provider default>")
                + "]";
    }
}
