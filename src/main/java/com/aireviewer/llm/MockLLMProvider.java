package com.aireviewer.llm;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/**
 * A permanent, offline {@link LLMProvider} that answers from a fixed template instead of calling a
 * model. It is not scaffolding to be deleted later — it has two lasting jobs.
 *
 * <p><b>1. The test double the whole package is built against.</b> The subject requires that the
 * application be testable without calling a real LLM, and CLAUDE.md §3 turns that into a hard
 * rule: no test may need a live provider. Everything downstream — the response validator, the
 * prompt builder, the resilience decorator, the analysis engine's LLM-backed analyzer, the facade
 * — is developed and asserted against this class. That only works if it is fast, deterministic and
 * free of side effects, so this provider performs no I/O, keeps no state, logs nothing, and
 * returns the same answer for the same request every time.
 *
 * <p><b>2. The last-resort fallback in production.</b> When the resilience decorator has exhausted
 * its retries against the real provider, it can fall back here so that one dead endpoint degrades
 * a single criterion's evaluation instead of aborting an entire analysis run. Note that
 * {@link #complete(LLMRequest)} always reports {@code fromFallback = false}: this provider cannot
 * know how it was reached, and honestly reports itself as a primary answer. Re-flagging the
 * response as fallback-derived is the decorator's job, since only the decorator knows that the
 * primary failed.
 *
 * <p>The returned content is a well-formed JSON document in the shape the response validator will
 * later enforce, so the validator's own tests have a realistic input. Only
 * {@link LLMRequest#criterionId()} is echoed into it — the untrusted project content is
 * deliberately never reflected back, which keeps the answer identical whatever project is being
 * analyzed and avoids re-emitting anything a malicious file tried to inject.
 *
 * <p>Immutable and therefore thread-safe, as {@link LLMProvider} requires.
 */
public final class MockLLMProvider implements LLMProvider {

    /** The identifier this provider reports; also the name the provider factory will select it by. */
    public static final String IDENTIFIER = "mock";

    // Field names of the evaluation schema. These must stay in sync with the response validator
    // (later step) — that is what makes this mock a usable input for the validator's tests.
    private static final String FIELD_CRITERION = "criterion";
    private static final String FIELD_SCORE = "score";
    private static final String FIELD_MAX_SCORE = "maxScore";
    private static final String FIELD_STRENGTHS = "strengths";
    private static final String FIELD_WEAKNESSES = "weaknesses";
    private static final String FIELD_RECOMMENDATIONS = "recommendations";

    private static final int PLACEHOLDER_SCORE = 14;
    private static final int PLACEHOLDER_MAX_SCORE = 20;

    private final boolean alwaysFails;

    /** Creates a provider that always answers successfully — the normal mode. */
    public MockLLMProvider() {
        this(false);
    }

    private MockLLMProvider(boolean alwaysFails) {
        this.alwaysFails = alwaysFails;
    }

    /**
     * Creates a provider whose every call fails with {@link LLMException.Kind#UNAVAILABLE}, as an
     * unreachable endpoint would.
     *
     * <p>This is how "the provider is down" is exercised without a network: retry counts, backoff,
     * fallback selection and the analysis engine's behaviour on a failed criterion are all tested
     * against this instance. {@code UNAVAILABLE} is the chosen kind because it is retryable, so it
     * drives the decorator's full retry path rather than short-circuiting it the way a
     * validation failure would.
     *
     * @return a provider that never succeeds
     */
    public static MockLLMProvider alwaysFailing() {
        return new MockLLMProvider(true);
    }

    @Override
    public LLMResponse complete(LLMRequest request) throws LLMException {
        Objects.requireNonNull(request, "request");

        if (alwaysFails) {
            throw new LLMException(
                    LLMException.Kind.UNAVAILABLE,
                    "mock provider is configured to fail for criterion '" + request.criterionId() + "'");
        }

        return new LLMResponse(evaluationJson(request.criterionId()), IDENTIFIER, false);
    }

    @Override
    public String describe() {
        return IDENTIFIER;
    }

    /**
     * Builds the placeholder evaluation as JSON.
     *
     * <p>Assembled through Jackson rather than by concatenating strings, so the result is
     * guaranteed well-formed even if a criterion id contains a quote or a backslash. Hand-rolled
     * JSON in the one package that must be careful about untrusted text would be exactly the wrong
     * example to set.
     */
    private static String evaluationJson(String criterionId) {
        JsonNodeFactory nodes = JsonNodeFactory.instance;
        ObjectNode root = nodes.objectNode();

        root.put(FIELD_CRITERION, criterionId);
        root.put(FIELD_SCORE, PLACEHOLDER_SCORE);
        root.put(FIELD_MAX_SCORE, PLACEHOLDER_MAX_SCORE);

        ArrayNode strengths = root.putArray(FIELD_STRENGTHS);
        strengths.add("Package responsibilities are clearly separated.");
        strengths.add("Interfaces are used at the module boundaries.");

        ArrayNode weaknesses = root.putArray(FIELD_WEAKNESSES);
        weaknesses.add("Some classes carry more than one responsibility.");

        ArrayNode recommendations = root.putArray(FIELD_RECOMMENDATIONS);
        recommendations.add("Split the largest class along its two responsibilities.");
        recommendations.add("Add tests for the error paths.");

        // ObjectNode.toString() serialises without throwing, keeping complete() free of an
        // impossible-to-trigger checked exception from the JSON writer.
        return root.toString();
    }
}
