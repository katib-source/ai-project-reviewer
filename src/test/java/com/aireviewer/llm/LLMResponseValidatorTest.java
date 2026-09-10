package com.aireviewer.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LLMResponseValidatorTest {

    private final LLMResponseValidator validator = new LLMResponseValidator();

    private static LLMResponse answer(String content) {
        return new LLMResponse(content, "test-model", false);
    }

    private LLMException failureFor(String content) {
        return assertThrows(LLMException.class, () -> validator.validate(answer(content)));
    }

    @Test
    @DisplayName("a valid answer parses into every field")
    void parsesValidAnswer() throws LLMException {
        String content = """
                {
                  "criterion": "coupling",
                  "score": 14,
                  "maxScore": 20,
                  "strengths": ["clear packages", "interfaces at the boundaries"],
                  "weaknesses": ["one oversized class"],
                  "recommendations": ["split the oversized class"]
                }""";

        LLMEvaluation evaluation = validator.validate(answer(content));

        assertEquals(Optional.of("coupling"), evaluation.criterion());
        assertEquals(14.0, evaluation.score());
        assertEquals(OptionalDouble.of(20.0), evaluation.maxScore());
        assertEquals(List.of("clear packages", "interfaces at the boundaries"), evaluation.strengths());
        assertEquals(List.of("one oversized class"), evaluation.weaknesses());
        assertEquals(List.of("split the oversized class"), evaluation.recommendations());
        assertFalse(evaluation.fromFallback(), "this answer came from the primary provider");
    }

    @Test
    @DisplayName("an answer wrapped in ```json fences still parses")
    void stripsJsonCodeFences() throws LLMException {
        String content = """
                ```json
                {"criterion": "patterns", "score": 17}
                ```""";

        LLMEvaluation evaluation = validator.validate(answer(content));

        assertEquals(17.0, evaluation.score());
        assertEquals(Optional.of("patterns"), evaluation.criterion());
    }

    @Test
    @DisplayName("an answer wrapped in bare fences, or fences on one line, still parses")
    void stripsOtherFenceShapes() throws LLMException {
        assertEquals(9.0, validator.validate(answer("```\n{\"score\": 9}\n```")).score());
        assertEquals(9.5, validator.validate(answer("```json {\"score\": 9.5} ```")).score());
    }

    @Test
    @DisplayName("a missing score fails schema validation")
    void rejectsMissingScore() {
        LLMException failure = failureFor("{\"criterion\": \"coupling\", \"strengths\": []}");

        assertEquals(LLMException.Kind.SCHEMA_VALIDATION, failure.kind());
        assertTrue(failure.getMessage().contains("score"), failure.getMessage());
    }

    @Test
    @DisplayName("a negative score fails schema validation")
    void rejectsNegativeScore() {
        LLMException failure = failureFor("{\"score\": -3}");

        assertEquals(LLMException.Kind.SCHEMA_VALIDATION, failure.kind());
    }

    @Test
    @DisplayName("a non-numeric score fails schema validation, with no coercion from text")
    void rejectsNonNumericScore() {
        assertEquals(LLMException.Kind.SCHEMA_VALIDATION, failureFor("{\"score\": \"14\"}").kind());
        assertEquals(LLMException.Kind.SCHEMA_VALIDATION, failureFor("{\"score\": null}").kind());
    }

    @Test
    @DisplayName("prose instead of JSON fails as malformed, quoting only a short snippet")
    void rejectsGarbage() {
        LLMException failure =
                failureFor("Sure! Here is my assessment of the project: it looks quite good overall.");

        assertEquals(LLMException.Kind.MALFORMED_RESPONSE, failure.kind());
        assertFalse(failure.getMessage().isBlank());
    }

    @Test
    @DisplayName("a huge malformed answer is truncated in the exception message")
    void truncatesLongSnippet() {
        LLMException failure = failureFor("not json ".repeat(5_000));

        assertEquals(LLMException.Kind.MALFORMED_RESPONSE, failure.kind());
        assertTrue(failure.getMessage().contains("truncated"), failure.getMessage());
        assertTrue(failure.getMessage().length() < 300,
                "exception messages reach the logs and must stay small: " + failure.getMessage().length());
    }

    @Test
    @DisplayName("an empty answer is reported as empty, not as malformed")
    void rejectsBlankAnswer() {
        assertEquals(LLMException.Kind.EMPTY_RESPONSE, failureFor("   \n  ").kind());
        assertEquals(LLMException.Kind.EMPTY_RESPONSE, failureFor("```json\n```").kind());
    }

    @Test
    @DisplayName("valid JSON that is not an object fails schema validation")
    void rejectsNonObjectJson() {
        assertEquals(LLMException.Kind.SCHEMA_VALIDATION, failureFor("[{\"score\": 12}]").kind());
    }

    @Test
    @DisplayName("a missing weaknesses array degrades to an empty list instead of failing")
    void toleratesMissingProseFields() throws LLMException {
        String content = "{\"score\": 12, \"strengths\": [\"tests exist\"]}";

        LLMEvaluation evaluation = validator.validate(answer(content));

        assertEquals(12.0, evaluation.score());
        assertEquals(List.of("tests exist"), evaluation.strengths());
        assertEquals(List.of(), evaluation.weaknesses());
        assertEquals(List.of(), evaluation.recommendations());
    }

    @Test
    @DisplayName("prose fields of the wrong type, and unstated scales, degrade rather than fail")
    void toleratesCosmeticTypeErrors() throws LLMException {
        String content = "{\"score\": 12, \"maxScore\": \"twenty\", \"weaknesses\": \"just one thing\"}";

        LLMEvaluation evaluation = validator.validate(answer(content));

        assertEquals(OptionalDouble.empty(), evaluation.maxScore());
        assertEquals(List.of(), evaluation.weaknesses());
    }

    @Test
    @DisplayName("the mock provider's own output validates end to end")
    void agreesWithMockProvider() throws LLMException {
        LLMRequest request = new LLMRequest(
                "senior software architect",
                "coupling",
                "Evaluate coupling.",
                "public class Foo {}",
                "see EvaluationSchema",
                0.2,
                800);

        LLMResponse response = new MockLLMProvider().complete(request);
        LLMEvaluation evaluation = validator.validate(response);

        // The producer and the enforcer of the schema must not drift apart.
        assertEquals(Optional.of("coupling"), evaluation.criterion());
        assertEquals(14.0, evaluation.score());
        assertEquals(OptionalDouble.of(20.0), evaluation.maxScore());
        assertFalse(evaluation.strengths().isEmpty());
        assertFalse(evaluation.weaknesses().isEmpty());
        assertFalse(evaluation.recommendations().isEmpty());
    }

    @Test
    @DisplayName("the fallback flag is carried from the response onto the evaluation")
    void carriesFallbackFlag() throws LLMException {
        LLMResponse primaryAnswer = new LLMResponse("{\"score\": 11}", "primary-model", false);
        LLMResponse fallbackAnswer = new LLMResponse("{\"score\": 11}", "mock", true);

        assertFalse(validator.validate(primaryAnswer).fromFallback());
        assertTrue(validator.validate(fallbackAnswer).fromFallback(),
                "a degraded result must stay identifiable after validation, or the report cannot "
                        + "tell a placeholder from a judgement");
    }

    @Test
    @DisplayName("the returned lists are immutable")
    void returnsImmutableLists() throws LLMException {
        LLMEvaluation evaluation = validator.validate(answer("{\"score\": 5, \"strengths\": [\"a\"]}"));

        assertThrows(UnsupportedOperationException.class, () -> evaluation.strengths().add("b"));
    }
}
