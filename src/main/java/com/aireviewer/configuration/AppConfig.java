package com.aireviewer.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Env-first application settings. Every other package in this project (directly, or indirectly
 * through {@code application}) reads its limits, paths and API keys from here rather than calling
 * {@code System.getenv} itself — one place to look, one place to change a default. No design
 * pattern is claimed for this class: there is exactly one real source of settings (the process
 * environment), so there is nothing here to swap at runtime.
 * <p>
 * Precedence for every value, highest first: an environment variable, then a matching key in
 * {@code application-local.properties} in the working directory (already git-ignored — see
 * CLAUDE.md §4), then a documented built-in default. A value that is simply absent from all three
 * is not an error. A value that is present but unusable (not a number, an empty list where at
 * least one entry is required) throws {@link ConfigException} immediately, rather than silently
 * falling back to the default and masking what is very likely a typo.
 * <p>
 * This class only reads and exposes values — it never validates that they name something real
 * (e.g. a known LLM provider). That check belongs to whichever package owns the concept, so that
 * this one doesn't have to know about every other package's domain.
 */
public final class AppConfig {

    private static final Set<String> DEFAULT_ALLOWED_EXTENSIONS = Set.of(
            "java", "kt", "py", "js", "jsx", "ts", "tsx", "go", "rb", "c", "h", "cpp", "cs", "php",
            "rs", "md", "json", "yml", "yaml", "xml", "properties"
    );
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 1_000_000L;
    private static final Set<String> DEFAULT_IGNORED_DIRECTORY_NAMES = Set.of("target", "node_modules", ".git");
    private static final String DEFAULT_LLM_PROVIDER = "mock";
    private static final Path DEFAULT_HISTORY_FILE_PATH = Path.of("history", "history.json");

    private static final String LOCAL_PROPERTIES_FILE_NAME = "application-local.properties";

    private final Map<String, String> values;

    private AppConfig(Map<String, String> values) {
        this.values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }

    /**
     * Builds a config directly from an explicit map, bypassing the real environment and any local
     * properties file entirely. This is what every test uses.
     */
    public static AppConfig from(Map<String, String> values) {
        return new AppConfig(values);
    }

    /**
     * Builds a config from the real process environment, overlaid on top of
     * {@code application-local.properties} if that file exists in the working directory. This is
     * the one call site {@code Main} (the composition root) is expected to use.
     */
    public static AppConfig fromEnvironment() {
        Map<String, String> merged = new HashMap<>(readPropertiesFile(Path.of(LOCAL_PROPERTIES_FILE_NAME)));
        merged.putAll(System.getenv());
        return new AppConfig(merged);
    }

    /**
     * Which LLM provider to use — a raw name (e.g. {@code "mock"}, {@code "mistral"}) handed
     * as-is to the {@code llm} package's provider factory. Defaults to {@code "mock"} so the whole
     * application runs end-to-end with no key and no network access. This class does not check
     * that the name is one the factory recognizes; an unknown name is the factory's failure to
     * report, not this class's.
     */
    public String llmProvider() {
        return stringOrDefault("LLM_PROVIDER", DEFAULT_LLM_PROVIDER);
    }

    /**
     * The Mistral API key, if one is configured. Empty rather than throwing when absent — asking
     * for this key does not by itself mean the caller requires it (the configured provider might
     * not even be Mistral), so an unset key is a normal, not exceptional, state. Never logged.
     */
    public Optional<String> mistralApiKey() {
        return nonBlank(values.get("MISTRAL_API_KEY"));
    }

    /**
     * File extensions (without the leading dot) that {@code project} includes when importing.
     */
    public Set<String> allowedFileExtensions() {
        String raw = values.get("PROJECT_ALLOWED_EXTENSIONS");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_ALLOWED_EXTENSIONS;
        }
        Set<String> parsed = new HashSet<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.startsWith(".")) {
                trimmed = trimmed.substring(1);
            }
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        if (parsed.isEmpty()) {
            throw new ConfigException("PROJECT_ALLOWED_EXTENSIONS is set but contains no usable entries: '" + raw + "'");
        }
        return Set.copyOf(parsed);
    }

    /**
     * Maximum size, in bytes, of a single file {@code project} will include during import.
     */
    public long maxFileSizeInBytes() {
        String raw = values.get("PROJECT_MAX_FILE_SIZE_BYTES");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_FILE_SIZE_BYTES;
        }
        long parsed;
        try {
            parsed = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new ConfigException("PROJECT_MAX_FILE_SIZE_BYTES is not a valid number: '" + raw + "'", e);
        }
        if (parsed < 0) {
            throw new ConfigException("PROJECT_MAX_FILE_SIZE_BYTES must be >= 0, got: " + parsed);
        }
        return parsed;
    }

    /**
     * Directory names {@code project} never descends into, regardless of depth.
     */
    public Set<String> ignoredDirectoryNames() {
        String raw = values.get("PROJECT_IGNORED_DIRECTORY_NAMES");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_IGNORED_DIRECTORY_NAMES;
        }
        Set<String> parsed = new HashSet<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        if (parsed.isEmpty()) {
            throw new ConfigException("PROJECT_IGNORED_DIRECTORY_NAMES is set but contains no usable entries: '" + raw + "'");
        }
        return Set.copyOf(parsed);
    }

    /**
     * Where {@code persistence} reads and writes the JSON history of past analyses.
     */
    public Path historyFilePath() {
        String raw = values.get("PERSISTENCE_HISTORY_FILE_PATH");
        return (raw == null || raw.isBlank()) ? DEFAULT_HISTORY_FILE_PATH : Path.of(raw);
    }

    private String stringOrDefault(String key, String defaultValue) {
        String raw = values.get(key);
        return (raw == null || raw.isBlank()) ? defaultValue : raw;
    }

    private static Optional<String> nonBlank(String value) {
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }

    /**
     * Reads a {@code .properties} file into a plain map, or returns an empty map if it doesn't
     * exist — a missing local overrides file is the common case (most environments have none),
     * not an error. Package-private and path-parameterized (rather than hardcoded to the working
     * directory) so it can be tested directly against a temp file, without a test depending on the
     * JVM's working directory or the real filesystem layout.
     */
    static Map<String, String> readPropertiesFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            throw new ConfigException("Failed to read " + path.toAbsolutePath(), e);
        }
        Map<String, String> asMap = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            asMap.put(name, properties.getProperty(name));
        }
        return asMap;
    }
}
