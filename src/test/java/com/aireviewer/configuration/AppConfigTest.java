package com.aireviewer.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void llmProviderDefaultsToMock() {
        AppConfig config = AppConfig.from(Map.of());

        assertEquals("mock", config.llmProvider());
    }

    @Test
    void llmProviderReadsTheConfiguredValue() {
        AppConfig config = AppConfig.from(Map.of("LLM_PROVIDER", "mistral"));

        assertEquals("mistral", config.llmProvider());
    }

    @Test
    void mistralApiKeyIsEmptyWhenUnset() {
        AppConfig config = AppConfig.from(Map.of());

        assertTrue(config.mistralApiKey().isEmpty());
    }

    @Test
    void mistralApiKeyIsEmptyWhenBlank() {
        AppConfig config = AppConfig.from(Map.of("MISTRAL_API_KEY", "   "));

        assertTrue(config.mistralApiKey().isEmpty());
    }

    @Test
    void mistralApiKeyReturnsTheConfiguredValue() {
        AppConfig config = AppConfig.from(Map.of("MISTRAL_API_KEY", "secret-key"));

        assertEquals("secret-key", config.mistralApiKey().orElseThrow());
    }

    @Test
    void groqApiKeyIsEmptyWhenUnset() {
        AppConfig config = AppConfig.from(Map.of());

        assertTrue(config.groqApiKey().isEmpty());
    }

    @Test
    void groqApiKeyIsEmptyWhenBlank() {
        AppConfig config = AppConfig.from(Map.of("GROQ_API_KEY", "   "));

        assertTrue(config.groqApiKey().isEmpty());
    }

    @Test
    void groqApiKeyReturnsTheConfiguredValue() {
        AppConfig config = AppConfig.from(Map.of("GROQ_API_KEY", "groq-secret"));

        assertEquals("groq-secret", config.groqApiKey().orElseThrow());
    }

    @Test
    void allowedFileExtensionsDefaultsToABroadSet() {
        AppConfig config = AppConfig.from(Map.of());

        Set<String> extensions = config.allowedFileExtensions();
        assertTrue(extensions.contains("java"));
        assertTrue(extensions.contains("md"));
    }

    @Test
    void allowedFileExtensionsParsesACommaSeparatedList() {
        AppConfig config = AppConfig.from(Map.of("PROJECT_ALLOWED_EXTENSIONS", "java, py"));

        assertEquals(Set.of("java", "py"), config.allowedFileExtensions());
    }

    @Test
    void allowedFileExtensionsStripsALeadingDot() {
        AppConfig config = AppConfig.from(Map.of("PROJECT_ALLOWED_EXTENSIONS", ".java,.md"));

        assertEquals(Set.of("java", "md"), config.allowedFileExtensions());
    }

    @Test
    void allowedFileExtensionsRejectsAListWithNoUsableEntries() {
        AppConfig config = AppConfig.from(Map.of("PROJECT_ALLOWED_EXTENSIONS", " , ,"));

        assertThrows(ConfigException.class, config::allowedFileExtensions);
    }

    @Test
    void maxFileSizeInBytesDefaultsToOneMegabyte() {
        AppConfig config = AppConfig.from(Map.of());

        assertEquals(1_000_000L, config.maxFileSizeInBytes());
    }

    @Test
    void maxFileSizeInBytesReadsTheConfiguredValue() {
        AppConfig config = AppConfig.from(Map.of("PROJECT_MAX_FILE_SIZE_BYTES", "500"));

        assertEquals(500L, config.maxFileSizeInBytes());
    }

    @Test
    void maxFileSizeInBytesRejectsANonNumericValue() {
        AppConfig config = AppConfig.from(Map.of("PROJECT_MAX_FILE_SIZE_BYTES", "not-a-number"));

        assertThrows(ConfigException.class, config::maxFileSizeInBytes);
    }

    @Test
    void maxFileSizeInBytesRejectsANegativeValue() {
        AppConfig config = AppConfig.from(Map.of("PROJECT_MAX_FILE_SIZE_BYTES", "-1"));

        assertThrows(ConfigException.class, config::maxFileSizeInBytes);
    }

    @Test
    void ignoredDirectoryNamesDefaultsToTheStandardSet() {
        AppConfig config = AppConfig.from(Map.of());

        assertEquals(Set.of("target", "node_modules", ".git"), config.ignoredDirectoryNames());
    }

    @Test
    void ignoredDirectoryNamesParsesACommaSeparatedList() {
        AppConfig config = AppConfig.from(Map.of("PROJECT_IGNORED_DIRECTORY_NAMES", "target, build"));

        assertEquals(Set.of("target", "build"), config.ignoredDirectoryNames());
    }

    @Test
    void ignoredDirectoryNamesRejectsAListWithNoUsableEntries() {
        AppConfig config = AppConfig.from(Map.of("PROJECT_IGNORED_DIRECTORY_NAMES", " , ,"));

        assertThrows(ConfigException.class, config::ignoredDirectoryNames);
    }

    @Test
    void historyFilePathDefaultsUnderTheHistoryDirectory() {
        AppConfig config = AppConfig.from(Map.of());

        assertEquals(Path.of("history", "history.json"), config.historyFilePath());
    }

    @Test
    void historyFilePathReadsTheConfiguredValue() {
        AppConfig config = AppConfig.from(Map.of("PERSISTENCE_HISTORY_FILE_PATH", "/tmp/custom-history.json"));

        assertEquals(Path.of("/tmp/custom-history.json"), config.historyFilePath());
    }

    @Test
    void readPropertiesFileReturnsEmptyMapWhenFileIsMissing(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.properties");

        assertEquals(Map.of(), AppConfig.readPropertiesFile(missing));
    }

    @Test
    void readPropertiesFileParsesAnExistingFile(@TempDir Path tempDir) throws IOException {
        Path propertiesFile = tempDir.resolve("application-local.properties");
        Files.writeString(propertiesFile, "LLM_PROVIDER=mistral\nMISTRAL_API_KEY=local-secret\n");

        Map<String, String> values = AppConfig.readPropertiesFile(propertiesFile);

        assertEquals("mistral", values.get("LLM_PROVIDER"));
        assertEquals("local-secret", values.get("MISTRAL_API_KEY"));
    }

    @Test
    void fromRejectsANullMap() {
        assertThrows(NullPointerException.class, () -> AppConfig.from(null));
    }
}
