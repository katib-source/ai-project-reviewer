package com.aireviewer.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LLMSettingsTest {

    @Test
    @DisplayName("defaults are offline and need no key, network or local server")
    void defaultsAreOffline() {
        LLMSettings settings = LLMSettings.defaults();

        assertEquals(LLMProviderFactory.KIND_MOCK, settings.kind());
        assertEquals("", settings.mistralApiKey());
        assertEquals(3, settings.maxAttempts());
        assertEquals(Duration.ofSeconds(2), settings.retryDelay());
        assertEquals(Optional.empty(), settings.providerTimeout(), "each provider keeps its own default");
    }

    @Test
    @DisplayName("the sampling defaults match what the prompt builder used before")
    void samplingDefaultsAreUnchanged() {
        LLMSettings settings = LLMSettings.defaults();

        assertEquals(0.2, settings.temperature());
        assertEquals(900, settings.maxOutputTokens());
    }

    @Test
    @DisplayName("with* methods change one thing and keep the rest")
    void withersCopyEverythingElse() {
        LLMSettings base = LLMSettings.defaults().withSampling(0.5, 400);

        LLMSettings mistral = base.withMistral("sk-key", "mistral-large-latest");

        assertEquals(LLMProviderFactory.KIND_MISTRAL, mistral.kind());
        assertEquals("sk-key", mistral.mistralApiKey());
        assertEquals("mistral-large-latest", mistral.mistralModel());
        assertEquals(0.5, mistral.temperature(), "sampling must survive a provider change");
        assertEquals(400, mistral.maxOutputTokens());
        assertEquals(LLMProviderFactory.KIND_MOCK, base.kind(), "the original is untouched");
    }

    @Test
    @DisplayName("withLocal selects the local kind and its server")
    void withLocalSelectsTheKind() {
        LLMSettings settings = LLMSettings.defaults().withLocal("http://127.0.0.1:5000/v1", "qwen");

        assertEquals(LLMProviderFactory.KIND_LOCAL, settings.kind());
        assertEquals("http://127.0.0.1:5000/v1", settings.localBaseUrl());
        assertEquals("qwen", settings.localModel());
    }

    @Test
    @DisplayName("null text values are stored as blank, so no accessor returns null")
    void normalizesNullsToBlank() {
        LLMSettings settings = LLMSettings.defaults().withMistral(null, null).withLocal(null, null);

        assertEquals("", settings.mistralApiKey());
        assertEquals("", settings.mistralModel());
        assertEquals("", settings.localBaseUrl());
        assertEquals("", settings.localModel());
    }

    @Test
    @DisplayName("unusable values are rejected at construction, not at the first request")
    void validatesEagerly() {
        LLMSettings base = LLMSettings.defaults();

        assertThrows(IllegalArgumentException.class, () -> base.withResilience(0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> base.withResilience(3, Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> base.withSampling(-0.1, 900));
        assertThrows(IllegalArgumentException.class, () -> base.withSampling(2.5, 900));
        assertThrows(IllegalArgumentException.class, () -> base.withSampling(0.2, 0));
        assertThrows(IllegalArgumentException.class, () -> base.withProviderTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> base.withKind(" "));
        assertThrows(NullPointerException.class, () -> base.withKind(null));
        assertThrows(NullPointerException.class, () -> base.withProviderTimeout(null));
    }

    @Test
    @DisplayName("toString never prints the API key")
    void toStringHidesTheKey() {
        String secret = "sk-super-secret-value-9999";

        String text = LLMSettings.defaults().withMistral(secret, "mistral-small-latest").toString();

        assertFalse(text.contains(secret), text);
        assertTrue(text.contains("<set>"), "but it should still say a key is configured: " + text);
        assertTrue(LLMSettings.defaults().toString().contains("<absent>"));
    }

    @Test
    @DisplayName("a provider timeout override is carried")
    void carriesProviderTimeoutOverride() {
        LLMSettings settings = LLMSettings.defaults().withProviderTimeout(Duration.ofSeconds(45));

        assertEquals(Optional.of(Duration.ofSeconds(45)), settings.providerTimeout());
    }
}
