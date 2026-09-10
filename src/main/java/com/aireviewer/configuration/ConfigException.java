package com.aireviewer.configuration;

/**
 * Thrown when a configuration value is present but cannot be used — malformed (e.g. a non-numeric
 * {@code PROJECT_MAX_FILE_SIZE_BYTES}), out of range, or naming an unknown option (e.g. an unknown
 * LLM provider name). A missing value is not an error and never throws this: it falls back to a
 * documented default. Thrown eagerly at startup so a typo in an environment variable fails loudly
 * instead of silently masking itself behind a default the operator never intended.
 */
public final class ConfigException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
