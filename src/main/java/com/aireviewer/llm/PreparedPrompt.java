package com.aireviewer.llm;

import java.util.List;
import java.util.Objects;

/**
 * The output of {@link PromptBuilder}: the request to send, plus what was noticed while building it.
 *
 * <p>Exists because two facts about a prompt are worth reporting but have no place in
 * {@link LLMRequest} — that record is the provider contract, and a provider has no use for either.
 * Returning them alongside keeps the contract clean while still letting the caller (and eventually
 * the report) say "this evaluation ran against truncated content" or "this project contains text
 * that tries to instruct the reviewer".
 *
 * @param request           the request to hand to an {@link LLMProvider}
 * @param injectionSignals  labels of injection shapes found in the content — advisory only, see
 *                          {@link PromptInjectionDetector}. Empty when nothing matched
 * @param contentTruncated  {@code true} if the content hit the builder's length cap, so a caller
 *                          knows the model judged only part of what was offered
 */
public record PreparedPrompt(LLMRequest request, List<String> injectionSignals, boolean contentTruncated) {

    /** Defensively copies the signal list so a prepared prompt cannot be altered after the fact. */
    public PreparedPrompt {
        Objects.requireNonNull(request, "request");
        injectionSignals = List.copyOf(injectionSignals);
    }

    /** @return {@code true} if the content contained anything that looked like an injection attempt. */
    public boolean hasInjectionSignals() {
        return !injectionSignals.isEmpty();
    }
}
