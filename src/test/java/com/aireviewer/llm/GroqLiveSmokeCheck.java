package com.aireviewer.llm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * <b>MANUAL ONLY — DO NOT RUN IN CI.</b>
 *
 * <p>This check talks to the real Groq API. It requires a real {@code GROQ_API_KEY}, makes a real
 * network call, and consumes real rate-limit quota (Groq's free tier needs no billing setup, which
 * is why this is the easier of the two live checks to run — but it is still a real request to a
 * third party, so it stays manual). Everything else in this package is tested offline against
 * {@link MockLLMProvider} or a loopback HTTP server, per CLAUDE.md §3; this exists only so a human
 * can confirm, once, that the whole chain works against an actual model — the subject requires a
 * genuine LLM call, and nothing offline can prove one happened.
 *
 * <h2>Why it never runs by accident</h2>
 * Two independent guards:
 * <ol>
 *   <li>The class is named {@code ...Check}, not {@code ...Test}, so Surefire's default discovery
 *       patterns ({@code Test*}, {@code *Test}, {@code *Tests}, {@code *TestCase}) do not see it.
 *       {@code mvn test} cannot pick it up, whatever the environment holds.</li>
 *   <li>{@link EnabledIfEnvironmentVariable} plus an assumption: with no key present it is skipped,
 *       not failed, so running it deliberately without a key reports "skipped" rather than red.</li>
 * </ol>
 * It is deliberately <em>not</em> {@code @Disabled}, so that a human who wants it can simply run it
 * from an IDE without editing the file first. Adding {@code @Disabled} would make the only intended
 * way to use it require a code change.
 *
 * <h2>How to run it</h2>
 * <pre>{@code
 * export GROQ_API_KEY='...'          # never commit this, never paste it into a chat
 * mvn test -Dtest=GroqLiveSmokeCheck -DfailIfNoTests=false
 * }</pre>
 *
 * <p>The model comes from {@link GroqLLMProvider#DEFAULT_MODEL}, whose Javadoc records when it was
 * last verified against Groq's published model list. Groq retires model IDs on a schedule, so if
 * this check fails with an {@link LLMException.Kind#HTTP_ERROR} mentioning the model, check that
 * list before assuming the code is broken.
 * It prints the evaluation it received to stdout, because the point is for a person to read the
 * model's actual answer and judge whether it is sensible. ({@code System.out} is fine here; the
 * CLAUDE.md §2 ban on printing applies to {@code src/main}.)
 *
 * <h2>Last verified</h2>
 * 2026-09-11, model {@code openai/gpt-oss-20b}: HTTP 200, unfenced JSON matching the schema
 * exactly, criterion echoed, {@code fromFallback=false}, 1569 ms round trip, score 3/20 on the
 * sample below with 8 accurate weaknesses. Evidence that the required genuine LLM call works end to
 * end — factory, prompt builder, HTTP adapter and validator — and the only log line emitted carried
 * no credential.
 *
 * <h2>What it asserts</h2>
 * Structure only — that a real answer came back, parsed, with a score inside the criterion's scale.
 * Never a particular score: a real model is not deterministic, and a check that demanded 14/20
 * would fail for the wrong reasons.
 *
 * <p>One assertion is worth its own note: {@link LLMEvaluation#fromFallback()} must be
 * {@code false}. The factory wraps every provider with a mock fallback, so a smoke check that
 * ignored this flag would <em>pass on a rejected API key</em> — the mock would quietly answer
 * instead, and the run would look like a successful conversation with Groq. Resilience is therefore
 * also set to a single attempt: this check should fail fast and loudly, not spend free-tier quota on
 * retries.
 */
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class GroqLiveSmokeCheck {

    /** A small, real piece of Java, deliberately mediocre so there is something to say about it. */
    private static final String SAMPLE_CODE = """
            package com.example;

            import java.util.*;

            public class DataHandler {
                public Map m = new HashMap();
                public void p(String k, Object v) {
                    if (k != null) { if (v != null) { m.put(k, v); } }
                }
                public Object g(String k) { return m.get(k); }
                // TODO: thread safety?
                public void clearAll() { m = new HashMap(); }
            }""";

    @Test
    @DisplayName("MANUAL: a real Groq call produces a usable evaluation")
    void realGroqCallProducesAUsableEvaluation() throws LLMException {
        String apiKey = System.getenv("GROQ_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "GROQ_API_KEY is not set: skipping the live check rather than failing");

        LLMSettings settings = LLMSettings.defaults()
                .withGroq(apiKey, GroqLLMProvider.DEFAULT_MODEL)
                // One attempt: fail fast rather than retrying against a metered API.
                .withResilience(1, Duration.ZERO);

        LLMProvider provider = LLMProviderFactory.create(settings);
        PromptBuilder promptBuilder = new PromptBuilder(settings);
        LLMResponseValidator validator = new LLMResponseValidator();

        PromptCriterion criterion = new PromptCriterion(
                "readability",
                "Code readability",
                "How readable and maintainable this code is: naming, structure, use of types, "
                        + "comments that earn their place, and anything that would slow a new "
                        + "reader down.",
                20);

        PreparedPrompt prepared = promptBuilder.prepare(criterion, SAMPLE_CODE);

        long startedAt = System.nanoTime();
        LLMResponse response = provider.complete(prepared.request());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        LLMEvaluation evaluation = validator.validate(response);

        print(provider, criterion, prepared, response, evaluation, elapsed);

        assertFalse(evaluation.fromFallback(),
                "a fallback answered, so the real Groq call FAILED and the mock replied instead "
                        + "- check the key, the model name and the network");
        assertNotNull(response.content());
        assertFalse(response.content().isBlank());

        double scale = evaluation.maxScore().orElse(criterion.maxScore());
        assertTrue(evaluation.score() >= 0 && evaluation.score() <= scale,
                "score " + evaluation.score() + " is outside 0.." + scale);
        assertTrue(evaluation.criterion().isEmpty()
                        || evaluation.criterion().get().equals(criterion.id()),
                "the model answered about a different criterion: " + evaluation.criterion());
    }

    /** Prints the answer for a human to read; never prints the API key. */
    private static void print(
            LLMProvider provider,
            PromptCriterion criterion,
            PreparedPrompt prepared,
            LLMResponse response,
            LLMEvaluation evaluation,
            Duration elapsed) {

        StringBuilder out = new StringBuilder()
                .append("\n=== LIVE GROQ SMOKE CHECK ===\n")
                .append("provider          : ").append(provider.describe()).append('\n')
                .append("answering model   : ").append(response.model()).append('\n')
                .append("criterion         : ").append(criterion.id())
                .append(" (").append(criterion.displayName()).append(")\n")
                .append("round trip        : ").append(elapsed.toMillis()).append(" ms\n")
                .append("from fallback     : ").append(evaluation.fromFallback()).append('\n')
                .append("content truncated : ").append(prepared.contentTruncated()).append('\n')
                .append("injection signals : ").append(prepared.injectionSignals()).append('\n')
                .append("--- raw content ---\n").append(response.content()).append('\n')
                .append("--- parsed evaluation ---\n")
                .append("score           : ").append(evaluation.score())
                .append(" / ").append(evaluation.maxScore().orElse(criterion.maxScore())).append('\n')
                .append("criterion echoed: ").append(evaluation.criterion().orElse("<absent>")).append('\n');

        appendList(out, "strengths", evaluation.strengths());
        appendList(out, "weaknesses", evaluation.weaknesses());
        appendList(out, "recommendations", evaluation.recommendations());

        System.out.println(out.append("=== END ===\n"));
    }

    private static void appendList(StringBuilder out, String label, List<String> values) {
        out.append(label).append(" (").append(values.size()).append("):\n");
        for (String value : values) {
            out.append("  - ").append(value).append('\n');
        }
    }
}
