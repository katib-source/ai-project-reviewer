package com.aireviewer.llm;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A model's evaluation of one criterion, after {@link LLMResponseValidator} has checked it — the
 * form the rest of the application consumes, so no caller ever has to touch raw JSON.
 *
 * <p>Exactly one field is mandatory: {@link #score()}. Everything else is best-effort, because a
 * model that answers with a usable score but forgets to list any weakness has still done the job,
 * and failing the whole criterion over a cosmetic omission would make evaluations needlessly
 * fragile. The optional fields are typed as {@link Optional} rather than defaulted to invented
 * values, so a caller can tell "the model said nothing" from "the model said this".
 *
 * @param criterion       the criterion id the model claims to have evaluated, if it echoed one
 *                        back; informational, since the caller already knows what it asked
 * @param score           the score awarded — always present, finite and non-negative
 * @param maxScore        the scale the score is on, if the model stated it. Deliberately not
 *                        defaulted here: the authoritative scale belongs to the criterion
 *                        definition on the analysis side, and this package has no business
 *                        inventing a grading scale
 * @param strengths       what the project does well; empty if the model gave none
 * @param weaknesses      what the project does badly; empty if the model gave none
 * @param recommendations suggested improvements; empty if the model gave none
 */
public record LLMEvaluation(
        Optional<String> criterion,
        double score,
        OptionalDouble maxScore,
        List<String> strengths,
        List<String> weaknesses,
        List<String> recommendations) {

    /** Defensively copies the lists so a validated evaluation cannot be mutated after the fact. */
    public LLMEvaluation {
        criterion = criterion == null ? Optional.empty() : criterion;
        maxScore = maxScore == null ? OptionalDouble.empty() : maxScore;
        strengths = List.copyOf(strengths);
        weaknesses = List.copyOf(weaknesses);
        recommendations = List.copyOf(recommendations);
    }
}
