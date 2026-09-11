package com.aireviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aireviewer.configuration.AppConfig;
import com.aireviewer.llm.LLMProviderFactory;
import com.aireviewer.llm.LLMSettings;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MainTest {

    private static final Map<String, String> BOTH_KEYS =
            Map.of("MISTRAL_API_KEY", "mistral-secret", "GROQ_API_KEY", "groq-secret");

    private static LLMSettings settingsFor(String provider) {
        Map<String, String> values = new HashMap<>(BOTH_KEYS);
        values.put("LLM_PROVIDER", provider);
        return Main.llmSettings(AppConfig.from(values));
    }

    @Test
    void groqIsSelectedWhenConfiguredEvenIfAMistralKeyIsPresent() {
        LLMSettings settings = settingsFor("groq");

        assertEquals(LLMProviderFactory.KIND_GROQ, settings.kind());
        assertEquals("groq-secret", settings.groqApiKey());
        assertEquals("mistral-secret", settings.mistralApiKey(), "the unused key is kept, so switching back is config-only");
    }

    @Test
    void mistralIsSelectedWhenConfiguredEvenIfAGroqKeyIsPresent() {
        LLMSettings settings = settingsFor("mistral");

        assertEquals(LLMProviderFactory.KIND_MISTRAL, settings.kind());
        assertEquals("mistral-secret", settings.mistralApiKey());
    }

    @Test
    void mockIsTheDefaultWhenNothingIsConfigured() {
        LLMSettings settings = Main.llmSettings(AppConfig.from(Map.of()));

        assertEquals(LLMProviderFactory.KIND_MOCK, settings.kind());
    }

    @Test
    void selectingAProviderWithoutItsKeyFailsAtStartupNamingTheVariable() {
        LLMSettings settings = Main.llmSettings(AppConfig.from(
                Map.of("LLM_PROVIDER", "groq", "MISTRAL_API_KEY", "mistral-secret")));

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> LLMProviderFactory.create(settings));

        assertTrue(failure.getMessage().contains("GROQ_API_KEY"), failure.getMessage());
    }
}
