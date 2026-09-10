package com.aireviewer.llm;

import java.util.Objects;

/**
 * Immutable answer from a model: the raw text, which model produced it, and whether it came from a
 * fallback provider instead of the configured primary one.
 *
 * <p>This type stays deliberately "dumb" — it carries text, it does not interpret it. No JSON
 * parsing, no score extraction, no schema checking happens here, because validating an answer is a
 * different concern from transporting one, and it fails differently: a transport problem is a dead
 * endpoint, a validation problem is a model that answered with prose where JSON was required. A
 * dedicated validator (later step) turns {@link #content()} into a checked structure and reports
 * {@link LLMException.Kind#MALFORMED_RESPONSE} or
 * {@link LLMException.Kind#SCHEMA_VALIDATION} failures. Keeping those apart means every provider
 * adapter shares one validator instead of each parsing for itself, and the validator is testable
 * against a plain string with no provider in sight.
 *
 * @param content      the raw text the model returned, exactly as received, never {@code null}
 * @param model        identifier of the model that actually answered — which may differ from the
 *                     one requested, e.g. after a fallback
 * @param fromFallback {@code true} if a fallback provider produced this answer rather than the
 *                     primary one; surfaced in the report so a reader knows an evaluation was
 *                     degraded rather than silently treating it as a first-class result
 */
public record LLMResponse(String content, String model, boolean fromFallback) {

    /** Rejects {@code null} text fields at construction — see {@link LLMRequest}. */
    public LLMResponse {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(model, "model");
    }
}
