package com.aireviewer.llm;

/**
 * <b>Adapter</b> pattern — the single stable contract the rest of the application depends on when
 * it wants an answer from a large language model.
 *
 * <p>Problem: every vendor exposes a different HTTP surface, a different JSON body shape, a
 * different authentication scheme and a different notion of "model". If any of that leaked out of
 * this package, then swapping Mistral for a locally hosted model — or for a deterministic mock in
 * tests — would mean editing the analysis engine, the facade and the tests all at once.
 *
 * <p>Each concrete provider is therefore an adapter: it translates an {@link LLMRequest} into
 * whatever its vendor expects, and translates the vendor's answer back into an {@link LLMResponse}
 * or an {@link LLMException}. Callers see only these three types, so a new provider is a new class
 * in this package and nothing else changes. Resilience (retry, timeout, fallback) is deliberately
 * <em>not</em> part of this contract: it is added by a decorator that implements this same
 * interface, which is only possible because the contract stays this small.
 *
 * <p>Implementations must be safe to call from multiple threads: the analysis engine may evaluate
 * several criteria concurrently against one provider instance.
 */
public interface LLMProvider {

    /**
     * Sends one request to the model and returns its answer.
     *
     * <p>The returned response is never {@code null}: a provider that has nothing to return throws
     * instead, so callers never have to distinguish "no answer" from "null answer".
     *
     * @param request the prompt material, never {@code null}
     * @return the model's answer, never {@code null}
     * @throws LLMException if the model could not be reached, timed out, or answered with
     *                      something unusable — see {@link LLMException.Kind}
     */
    LLMResponse complete(LLMRequest request) throws LLMException;

    /**
     * A short, human-readable identifier for this provider and the model behind it, such as
     * {@code "mistral:mistral-small-latest"}, {@code "lmstudio:local-model"} or {@code "mock"}.
     *
     * <p>Used in logs and in the generated report so a reader can tell which engine produced an
     * evaluation. It must never contain an API key or any other secret.
     *
     * @return the identifier, never {@code null} and never blank
     */
    String describe();
}
