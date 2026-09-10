/**
 * Everything to do with talking to a large language model: one stable provider contract, adapters
 * for concrete vendors, resilience, prompt construction with prompt-injection defense, and
 * validation of what comes back.
 *
 * <h2>What a caller outside this package needs to know</h2>
 * Five types, and nothing else:
 * <ul>
 *   <li>{@link com.aireviewer.llm.LLMProviderFactory} — builds a provider from configuration values.
 *       Called <b>once</b>, by the composition root in {@code application}.</li>
 *   <li>{@link com.aireviewer.llm.LLMProvider} — the contract you are handed. Never construct a
 *       concrete provider yourself.</li>
 *   <li>{@link com.aireviewer.llm.PromptBuilder} with
 *       {@link com.aireviewer.llm.PromptCriterion} — turns a criterion plus project content into a
 *       request. Never assemble an {@link com.aireviewer.llm.LLMRequest} by hand.</li>
 *   <li>{@link com.aireviewer.llm.LLMResponseValidator} — turns the raw answer into a checked
 *       {@link com.aireviewer.llm.LLMEvaluation}.</li>
 *   <li>{@link com.aireviewer.llm.LLMException} — the one failure type, carrying a
 *       {@link com.aireviewer.llm.LLMException.Kind}.</li>
 * </ul>
 *
 * <h2>The handoff: an LLM-backed analyzer</h2>
 * This is the intended shape of the bridge in {@code analysis/analyzers}. The provider arrives by
 * <b>constructor injection</b> — the analyzer must not call the factory, must not know which vendor
 * it got, and must not import a concrete provider class (CLAUDE.md §0).
 *
 * <pre>{@code
 * public final class LlmCriterionAnalyzer extends AbstractAnalyzer {
 *
 *     private final LLMProvider provider;          // injected: could be mock, Mistral, local
 *     private final PromptBuilder promptBuilder;   // one instance is enough, it is thread-safe
 *     private final LLMResponseValidator validator;
 *
 *     public LlmCriterionAnalyzer(LLMProvider provider) {
 *         this.provider = provider;
 *         this.promptBuilder = new PromptBuilder();
 *         this.validator = new LLMResponseValidator();
 *     }
 *
 *     // Called by the engine for one criterion. Wrap in a failed result on LLMException;
 *     // AbstractAnalyzer's template method already does that for you.
 *     protected CriterionResult evaluate(String criterionId, String projectContent)
 *             throws LLMException {
 *
 *         // 1. Describe the criterion in this package's terms.
 *         PromptCriterion criterion = new PromptCriterion(
 *                 criterionId,
 *                 "Coupling between packages",
 *                 "How loosely coupled the packages are, and whether dependencies flow one way.",
 *                 20);
 *
 *         // 2. Build the request. projectContent is UNTRUSTED: pass it as the second argument
 *         //    and nowhere else. prepare() also reports truncation and injection signals;
 *         //    build() is the short form when you do not need them.
 *         PreparedPrompt prepared = promptBuilder.prepare(criterion, projectContent);
 *
 *         // 3. Send it. Retry, timeout and fallback already happen inside the provider.
 *         LLMResponse response = provider.complete(prepared.request());
 *
 *         // 4. Validate before believing anything. Throws LLMException on a bad answer.
 *         LLMEvaluation evaluation = validator.validate(response);
 *
 *         // 5. Map to your own type. Optional fields really are optional.
 *         double score = evaluation.score();
 *         double scale = evaluation.maxScore().orElse(criterion.maxScore());
 *         boolean degraded = response.fromFallback();  // worth reporting: not the primary model
 *         return CriterionResult.of(criterionId, score, scale, evaluation.strengths(),
 *                 evaluation.weaknesses(), evaluation.recommendations(), degraded);
 *     }
 * }
 * }</pre>
 *
 * And once, in the composition root:
 *
 * <pre>{@code
 * LLMProvider provider = LLMProviderFactory.create(
 *         config.llmKind(),          // "mock" | "mistral" | "local"
 *         config.mistralApiKey(),    // null or blank is fine unless kind is "mistral"
 *         config.mistralModel(),     // null or blank uses the provider default
 *         config.localBaseUrl(),
 *         config.localModel(),
 *         config.maxAttempts(),      // e.g. 3
 *         config.retryDelay());      // e.g. Duration.ofSeconds(2)
 *
 * engine.register(new LlmCriterionAnalyzer(provider));
 * }</pre>
 *
 * <h2>Rules for callers</h2>
 * <ul>
 *   <li><b>Never concatenate instructions and project content.</b> The separation between
 *       {@link com.aireviewer.llm.LLMRequest#instructions()} and
 *       {@link com.aireviewer.llm.LLMRequest#untrustedContent()} is what both injection defenses
 *       rest on: the delimiters written by {@link com.aireviewer.llm.PromptBuilder}, and the
 *       system-vs-user message split made by the HTTP adapters. Merge them and both are gone.</li>
 *   <li><b>Always validate.</b> A model answers with prose, markdown fences and out-of-range
 *       numbers as a matter of routine. Never read
 *       {@link com.aireviewer.llm.LLMResponse#content()} directly.</li>
 *   <li><b>Expect {@link com.aireviewer.llm.LLMException} on every call</b> and turn it into a
 *       failed result for that criterion. One unavailable provider must not abort an analysis run.
 *       {@link com.aireviewer.llm.LLMException.Kind#isRetryable()} is already handled inside the
 *       provider — do not add retry logic of your own.</li>
 *   <li><b>Report {@link com.aireviewer.llm.LLMResponse#fromFallback()}.</b> {@code true} means the
 *       configured provider failed and a fallback answered, so the evaluation is degraded; a reader
 *       of the report deserves to know that.</li>
 *   <li><b>Never log an API key, a request body or a whole file's content</b> (CLAUDE.md §4).</li>
 *   <li><b>Tests never touch the network.</b> Inject {@link com.aireviewer.llm.MockLLMProvider} for
 *       the happy path and {@link com.aireviewer.llm.MockLLMProvider#alwaysFailing()} for failure
 *       paths. If a test needs a real HTTP server, do what
 *       {@code OpenAiCompatibleLLMProviderTest} does and start one on loopback.</li>
 * </ul>
 *
 * <h2>Design patterns in this package</h2>
 * <ul>
 *   <li><b>Adapter</b> — {@link com.aireviewer.llm.LLMProvider} and its implementations:
 *       {@link com.aireviewer.llm.MistralLLMProvider}, {@link com.aireviewer.llm.LocalLmStudioProvider},
 *       {@link com.aireviewer.llm.MockLLMProvider}, sharing
 *       {@link com.aireviewer.llm.OpenAiCompatibleLLMProvider}.</li>
 *   <li><b>Decorator</b> — {@link com.aireviewer.llm.ResilientLLMProvider} adds retry, timeout
 *       handling and fallback to any provider without touching one.</li>
 *   <li><b>Factory</b> — {@link com.aireviewer.llm.LLMProviderFactory} is the only place that names
 *       a concrete provider.</li>
 * </ul>
 */
package com.aireviewer.llm;
