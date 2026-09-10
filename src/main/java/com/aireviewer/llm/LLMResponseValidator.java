package com.aireviewer.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * Turns the raw text of an {@link LLMResponse} into a checked {@link LLMEvaluation}, or fails with
 * an {@link LLMException}.
 *
 * <p>This is the package's quality gate. A language model is a text generator, not an API: it will
 * wrap JSON in markdown fences, answer in prose, omit a field, or return a score of {@code -3} or
 * {@code "fourteen"}. None of that may reach the analysis engine, the consolidated score or the
 * generated report, because a bad value that flows onward becomes a wrong grade in a PDF, which is
 * far harder to notice than a loud failure here. So every answer is parsed and checked at this one
 * boundary, and anything unusable becomes a typed failure the caller can react to.
 *
 * <p>Kept separate from the provider adapters (rather than each one parsing its own answers) for
 * two reasons: every provider then shares exactly one notion of "valid", and this class is
 * testable against a plain string with no provider, no network and no mocking framework. It is
 * also why {@link LLMResponse} stays a dumb carrier — transport and interpretation fail
 * differently and belong apart.
 *
 * <p>Strictness is deliberately uneven. {@link EvaluationSchema#FIELD_SCORE} is mandatory: without
 * it there is nothing to consolidate. The prose lists are best-effort: a missing
 * {@code weaknesses} array yields an empty list rather than discarding an otherwise perfectly good
 * evaluation. Being lenient about decoration and strict about the number is what keeps LLM-backed
 * criteria usable in practice.
 *
 * <p>It also carries {@link LLMResponse#fromFallback()} across into the
 * {@link LLMEvaluation}. Validation is the last place where the raw response and the parsed result
 * are both in hand, so a flag not copied here is a flag that silently disappears.
 *
 * <p>Stateless and thread-safe: the analysis engine may validate several answers concurrently.
 */
public final class LLMResponseValidator {

    /** Jackson's mapper is thread-safe once configured, so one shared instance is enough. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Opening markdown fence, with an optional language tag: <code>```</code> or <code>```json</code>. */
    private static final Pattern OPENING_FENCE = Pattern.compile("^```[A-Za-z0-9_+-]*[ \\t]*\\R?");

    /** Closing markdown fence at the very end of the text. */
    private static final Pattern CLOSING_FENCE = Pattern.compile("\\s*```\\s*$");

    /**
     * How much of an unparseable answer is quoted in an exception message. Exception messages end
     * up in logs, so they must stay small and must never carry a whole model answer (which can be
     * kilobytes, and is untrusted text we have no reason to spread around).
     */
    private static final int SNIPPET_LIMIT = 120;

    /**
     * Validates one model answer.
     *
     * @param response the raw answer, never {@code null}
     * @return the validated evaluation, never {@code null}
     * @throws LLMException {@link LLMException.Kind#EMPTY_RESPONSE} if there is no text to read,
     *                      {@link LLMException.Kind#MALFORMED_RESPONSE} if the text is not JSON,
     *                      {@link LLMException.Kind#SCHEMA_VALIDATION} if it is JSON but does not
     *                      satisfy the contract
     */
    public LLMEvaluation validate(LLMResponse response) throws LLMException {
        Objects.requireNonNull(response, "response");

        String payload = stripCodeFences(response.content());
        if (payload.isBlank()) {
            // EMPTY_RESPONSE rather than MALFORMED_RESPONSE: there is nothing to have malformed,
            // and the distinction tells whoever reads the log whether the model said something
            // unusable or said nothing at all.
            throw new LLMException(
                    LLMException.Kind.EMPTY_RESPONSE, "model returned no content to validate");
        }

        JsonNode root = parse(payload);
        if (!root.isObject()) {
            throw new LLMException(
                    LLMException.Kind.SCHEMA_VALIDATION,
                    "expected a JSON object at the top level but found " + root.getNodeType());
        }

        return new LLMEvaluation(
                readCriterion(root),
                readScore(root),
                readMaxScore(root),
                readStringList(root, EvaluationSchema.FIELD_STRENGTHS),
                readStringList(root, EvaluationSchema.FIELD_WEAKNESSES),
                readStringList(root, EvaluationSchema.FIELD_RECOMMENDATIONS),
                // Provenance travels with the result. Validation is the last point where both the
                // raw response and the parsed evaluation are in hand, so if the flag is not copied
                // here it is lost for good.
                response.fromFallback());
    }

    /**
     * Removes a surrounding markdown code fence if the model added one.
     *
     * <p>Asking for "JSON only" does not reliably get JSON only — chat-tuned models routinely
     * answer with <code>```json ... ```</code> because that is how they were trained to present
     * code. Stripping it is a one-line defense against the single most common way a
     * perfectly good answer would otherwise be rejected as malformed. Anything else the model adds
     * around the JSON is still a failure: this strips known decoration, it does not go hunting for
     * JSON inside arbitrary prose.
     */
    private static String stripCodeFences(String content) {
        String text = content.strip();
        if (!text.startsWith("```")) {
            return text;
        }
        text = OPENING_FENCE.matcher(text).replaceFirst("");
        return CLOSING_FENCE.matcher(text).replaceFirst("").strip();
    }

    private static JsonNode parse(String payload) throws LLMException {
        try {
            return MAPPER.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new LLMException(
                    LLMException.Kind.MALFORMED_RESPONSE,
                    "model answer is not valid JSON: " + snippet(payload),
                    e);
        }
    }

    private static Optional<String> readCriterion(JsonNode root) {
        JsonNode node = root.get(EvaluationSchema.FIELD_CRITERION);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(node.asText());
    }

    /** The one hard requirement: a finite, non-negative number. No coercion from strings. */
    private static double readScore(JsonNode root) throws LLMException {
        JsonNode node = root.get(EvaluationSchema.FIELD_SCORE);
        if (node == null || node.isNull()) {
            throw new LLMException(
                    LLMException.Kind.SCHEMA_VALIDATION,
                    "required field '" + EvaluationSchema.FIELD_SCORE + "' is missing");
        }
        if (!node.isNumber()) {
            throw new LLMException(
                    LLMException.Kind.SCHEMA_VALIDATION,
                    "field '" + EvaluationSchema.FIELD_SCORE + "' must be a number but was "
                            + node.getNodeType());
        }
        double score = node.asDouble();
        if (!Double.isFinite(score) || score < 0) {
            throw new LLMException(
                    LLMException.Kind.SCHEMA_VALIDATION,
                    "field '" + EvaluationSchema.FIELD_SCORE
                            + "' must be finite and non-negative but was " + score);
        }
        return score;
    }

    /**
     * Optional, and never a reason to fail: a stated scale that is missing or nonsensical is simply
     * treated as unstated, leaving the authoritative scale to the criterion definition on the
     * analysis side.
     */
    private static OptionalDouble readMaxScore(JsonNode root) {
        JsonNode node = root.get(EvaluationSchema.FIELD_MAX_SCORE);
        if (node == null || !node.isNumber()) {
            return OptionalDouble.empty();
        }
        double maxScore = node.asDouble();
        if (!Double.isFinite(maxScore) || maxScore <= 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(maxScore);
    }

    /**
     * Reads one of the prose lists. A missing field, a field that is not an array, or elements that
     * are not simple values all degrade to "nothing said here" instead of failing the evaluation —
     * these fields decorate a report section, they do not carry the grade.
     */
    private static List<String> readStringList(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(node.size());
        for (JsonNode element : node) {
            if (element.isValueNode() && !element.asText().isBlank()) {
                values.add(element.asText().strip());
            }
        }
        return List.copyOf(values);
    }

    /** Single-line, length-capped quote of an offending answer, safe to put in a log message. */
    private static String snippet(String payload) {
        String flattened = payload.replaceAll("\\s+", " ").strip();
        if (flattened.length() <= SNIPPET_LIMIT) {
            return '"' + flattened + '"';
        }
        return '"' + flattened.substring(0, SNIPPET_LIMIT) + "\" (truncated, "
                + payload.length() + " chars total)";
    }
}
