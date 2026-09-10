package com.aireviewer.llm;

/**
 * The field names of the JSON evaluation we ask a model to produce — the single source of truth for
 * that contract inside this package.
 *
 * <p>Not a design pattern, just de-duplication with a purpose: three separate places have to agree
 * on these names or the pipeline silently breaks. {@link MockLLMProvider} <em>produces</em> a
 * document in this shape, {@link LLMResponseValidator} <em>enforces</em> it, and the prompt builder
 * (later step) has to <em>describe</em> it to the model. When each owned a private copy of the
 * strings, renaming a field meant finding every copy, and a missed one would only show up as a
 * validation failure at runtime.
 *
 * <p>Package-private on purpose: nothing outside {@code llm} needs the wire format. Consumers get
 * the parsed {@link LLMEvaluation} instead, which is what keeps the JSON shape an implementation
 * detail of this package.
 */
final class EvaluationSchema {

    /** Id of the criterion the evaluation answers; echoed back by the model as a cross-check. */
    static final String FIELD_CRITERION = "criterion";

    /** The score awarded. The only field {@link LLMResponseValidator} treats as mandatory. */
    static final String FIELD_SCORE = "score";

    /** The scale the score is expressed on, e.g. 20. Optional — see {@link LLMEvaluation#maxScore()}. */
    static final String FIELD_MAX_SCORE = "maxScore";

    /** What the analyzed project does well, as a list of short strings. */
    static final String FIELD_STRENGTHS = "strengths";

    /** What the analyzed project does badly, as a list of short strings. */
    static final String FIELD_WEAKNESSES = "weaknesses";

    /** Suggested improvements, as a list of short strings. */
    static final String FIELD_RECOMMENDATIONS = "recommendations";

    private EvaluationSchema() {
        // Constants holder; never instantiated.
    }
}
