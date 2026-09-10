package com.aireviewer.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LLMProviderFactoryTest {

    private static final Duration NO_DELAY = Duration.ZERO;

    private static LLMProvider create(String kind, String mistralKey, String mistralModel,
            String localBaseUrl, String localModel) {
        return LLMProviderFactory.create(
                kind, mistralKey, mistralModel, localBaseUrl, localModel, 1, NO_DELAY);
    }

    private static LLMRequest request() {
        return new PromptBuilder().build(
                new PromptCriterion("coupling", "Coupling", "How loosely coupled the packages are.", 20),
                "public class Foo {}");
    }

    /** Binds a port and releases it, so nothing is listening there. */
    private static String closedLocalPortUrl() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":"
                    + socket.getLocalPort() + "/v1";
        }
    }

    @Test
    @DisplayName("kind 'mock' builds the offline provider")
    void buildsMock() {
        LLMProvider provider = create(LLMProviderFactory.KIND_MOCK, null, null, null, null);

        assertEquals("mock", provider.describe());
    }

    @Test
    @DisplayName("kind 'mistral' builds the Mistral adapter, named through the wrapper")
    void buildsMistral() {
        LLMProvider provider =
                create(LLMProviderFactory.KIND_MISTRAL, "sk-test-key", "mistral-large-latest", null, null);

        // describe() delegates through ResilientLLMProvider, so this also re-checks that wiring.
        assertEquals("mistral:mistral-large-latest", provider.describe());
    }

    @Test
    @DisplayName("kind 'local' builds the LM Studio adapter")
    void buildsLocal() {
        LLMProvider provider = create(
                LLMProviderFactory.KIND_LOCAL, null, null, "http://127.0.0.1:9999/v1", "qwen-coder");

        assertEquals("lmstudio:qwen-coder", provider.describe());
    }

    @Test
    @DisplayName("blank model names and URLs fall back to the provider defaults")
    void appliesProviderDefaults() {
        assertEquals("mistral:" + MistralLLMProvider.DEFAULT_MODEL,
                create(LLMProviderFactory.KIND_MISTRAL, "sk-test-key", "  ", null, null).describe());
        assertEquals("lmstudio:" + LocalLmStudioProvider.DEFAULT_MODEL,
                create(LLMProviderFactory.KIND_LOCAL, null, null, null, null).describe());
    }

    @Test
    @DisplayName("the kind string is case- and whitespace-insensitive")
    void normalizesTheKind() {
        assertEquals("mock", create("  MOCK ", null, null, null, null).describe());
        assertEquals("mistral:" + MistralLLMProvider.DEFAULT_MODEL,
                create("Mistral", "sk-test-key", null, null, null).describe());
    }

    @Test
    @DisplayName("an unknown kind fails fast, listing the valid kinds")
    void rejectsUnknownKind() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> create("gpt-4", "sk-test-key", null, null, null));

        assertTrue(failure.getMessage().contains("gpt-4"), failure.getMessage());
        assertTrue(failure.getMessage().contains(LLMProviderFactory.KIND_MOCK));
        assertTrue(failure.getMessage().contains(LLMProviderFactory.KIND_MISTRAL));
        assertTrue(failure.getMessage().contains(LLMProviderFactory.KIND_LOCAL));
    }

    @Test
    @DisplayName("a missing Mistral key gives an actionable message, not a bare NullPointerException")
    void rejectsMissingMistralKeyWithAClearMessage() {
        for (String noKey : new String[] {null, "", "   "}) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> create(LLMProviderFactory.KIND_MISTRAL, noKey, null, null, null),
                    "key was: " + noKey);

            String message = failure.getMessage();
            assertTrue(message.contains("mistral"), message);
            assertTrue(message.contains("API key"), message);
            assertTrue(message.contains("MISTRAL_API_KEY"), "must say where the key comes from: " + message);
            assertTrue(message.contains(LLMProviderFactory.KIND_MOCK),
                    "must offer the offline way out: " + message);
            assertEquals(IllegalArgumentException.class, failure.getCause().getClass(),
                    "the provider's own validation stays the authority, and is kept as the cause");
        }
    }

    @Test
    @DisplayName("a null kind is rejected")
    void rejectsNullKind() {
        assertThrows(NullPointerException.class, () -> create(null, null, null, null, null));
    }

    @Test
    @DisplayName("bad resilience settings are rejected by the decorator's own validation")
    void rejectsBadResilienceSettings() {
        assertThrows(IllegalArgumentException.class, () -> LLMProviderFactory.create(
                LLMProviderFactory.KIND_MOCK, null, null, null, null, 0, NO_DELAY));
        assertThrows(IllegalArgumentException.class, () -> LLMProviderFactory.create(
                LLMProviderFactory.KIND_MOCK, null, null, null, null, 3, Duration.ofMillis(-1)));
    }

    @Test
    @DisplayName("every product is wrapped in resilience, including the mock")
    void alwaysWrapsInResilience() {
        assertInstanceOf(ResilientLLMProvider.class,
                create(LLMProviderFactory.KIND_MOCK, null, null, null, null));
        assertInstanceOf(ResilientLLMProvider.class,
                create(LLMProviderFactory.KIND_MISTRAL, "sk-test-key", null, null, null));
        assertInstanceOf(ResilientLLMProvider.class,
                create(LLMProviderFactory.KIND_LOCAL, null, null, null, null));
    }

    @Test
    @DisplayName("a dead local server falls back to a usable mock answer instead of failing")
    void resilienceIsRealNotDecorative() throws Exception {
        LLMProvider provider =
                create(LLMProviderFactory.KIND_LOCAL, null, null, closedLocalPortUrl(), "local-model");

        LLMResponse response = provider.complete(request());

        assertTrue(response.fromFallback(), "the unreachable server must have been replaced");
        assertEquals("mock", response.model());
        // And the fallback answer is genuinely usable, not just non-null.
        LLMEvaluation evaluation = new LLMResponseValidator().validate(response);
        assertEquals("coupling", evaluation.criterion().orElseThrow());
        assertEquals(14.0, evaluation.score());
    }

    @Test
    @DisplayName("the documented three-step usage works end to end with the factory's product")
    void handoffExampleWorks() throws LLMException {
        // Exactly the sequence package-info.java tells a teammate to write.
        LLMProvider provider = LLMProviderFactory.create(
                LLMProviderFactory.KIND_MOCK, null, null, null, null, 3, NO_DELAY);
        PromptBuilder promptBuilder = new PromptBuilder();
        LLMResponseValidator validator = new LLMResponseValidator();

        PromptCriterion criterion = new PromptCriterion(
                "coupling", "Coupling between packages", "How loosely coupled the packages are.", 20);
        PreparedPrompt prepared = promptBuilder.prepare(criterion, "public class Foo {}");
        LLMResponse response = provider.complete(prepared.request());
        LLMEvaluation evaluation = validator.validate(response);

        assertEquals("coupling", evaluation.criterion().orElseThrow());
        assertTrue(evaluation.score() >= 0);
        assertEquals(20.0, evaluation.maxScore().orElse(criterion.maxScore()));
        assertFalse(prepared.hasInjectionSignals());
        assertFalse(response.fromFallback());
    }
}
