package com.aireviewer.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Scans untrusted project content for text that looks like an attempt to give the model
 * instructions, and reports which shapes matched.
 *
 * <p><b>Advisory only — this must never block an analysis.</b> The actual defense against prompt
 * injection is structural: {@link PromptBuilder} keeps the data in its own delimited field, tells
 * the model that block carries no authority, and constrains the answer to a narrow schema. That
 * defense does not depend on recognising the attack, which is the point — a keyword scanner will
 * always be one paraphrase behind, and treating it as a gate would mean a project could be refused
 * analysis because a source file innocently says "ignore the instructions above" in a comment about
 * its own API. So this class only produces labels for visibility: logged next to the criterion, and
 * surfaced to the caller through {@link PreparedPrompt#injectionSignals()}.
 *
 * <p>False positives are expected and acceptable. A project that itself does LLM grading — this one,
 * for instance — legitimately contains JSON with a {@code score} field and prose about ignoring
 * instructions, and will light up several signals. That is why nothing branches on the result.
 *
 * <p>These findings could later feed the security criterion's report section ("the analyzed project
 * contains text that attempts to manipulate an automated reviewer"), which would turn an attack into
 * a genuine finding about the project. Not wired up — the analysis side owns that decision.
 *
 * <p>Scanning happens <em>after</em> {@link PromptBuilder} sanitizes the content, so zero-width
 * characters and homoglyphs cannot hide a phrase from these patterns. Stateless and thread-safe.
 */
public final class PromptInjectionDetector {

    /**
     * Label → pattern, in a {@link LinkedHashMap} so the reported order is stable and tests can
     * assert on it. Patterns are phrase-shaped rather than keyword-shaped: matching the bare word
     * "system" or "instructions" would fire on most real codebases and make the signal worthless.
     */
    private static final Map<String, Pattern> SIGNALS = buildSignals();

    private static Map<String, Pattern> buildSignals() {
        Map<String, Pattern> signals = new LinkedHashMap<>();

        // "ignore all previous instructions", "disregard the rules above", ...
        // Both word orders matter: the qualifier can precede the noun ("previous instructions") or
        // follow it ("the rules above"), and only matching one of them would miss half the phrasings.
        String verb = "\\b(ignore|disregard|forget|override|bypass)\\b";
        String qualifier = "\\b(previous|prior|earlier|above|preceding|all)\\b";
        String noun = "\\b(instruction|instructions|prompt|prompts|rule|rules|direction|directions)\\b";
        String gap = "[^\\n]{0,30}";
        signals.put("instruction-override", Pattern.compile(
                "(?i)" + verb + gap + "(" + qualifier + gap + noun + "|" + noun + gap + qualifier + ")"));

        // Text pretending to be a new conversational turn or a system prompt. The line-start
        // anchor tolerates comment markers, because the usual hiding place is a code comment:
        // "// SYSTEM:", "# SYSTEM:", " * SYSTEM:".
        signals.put("fake-system-turn", Pattern.compile(
                "(?im)^[\\s/*#>|-]{0,6}(system|assistant|developer)\\s*:"
                        + "|<\\|im_(start|end)\\|>"
                        + "|#{2,}\\s*(system|instructions?)\\s*#{2,}"
                        + "|\\[\\s*(system|system prompt)\\s*\\]"));

        // Attempts to reassign the model's role.
        signals.put("role-change", Pattern.compile(
                "(?i)\\byou are now\\b"
                        + "|\\bfrom now on\\b[^\\n]{0,25}\\byou\\b"
                        + "|\\bpretend to be\\b"
                        + "|\\bact as\\b[^\\n]{0,20}\\b(assistant|model|reviewer|grader|evaluator)\\b"
                        + "|\\byour new (role|task|instructions?)\\b"));

        // Direct attempts to dictate the grade.
        signals.put("score-manipulation", Pattern.compile(
                "(?i)\\b(give|award|grant|assign|rate|set)\\b[^\\n]{0,40}"
                        + "\\b(10\\s*/\\s*10|20\\s*/\\s*20|100\\s*%|maximum score|max score|full marks"
                        + "|perfect score|highest score|top score)\\b"));

        // A ready-made answer planted in the data, hoping to be echoed back. Keyed to our own
        // schema via EvaluationSchema so it tracks the field name rather than a stale literal.
        signals.put("embedded-answer", Pattern.compile(
                "(?i)[\"']" + Pattern.quote(EvaluationSchema.FIELD_SCORE) + "[\"']\\s*:\\s*-?\\d"));

        // Collections.unmodifiableMap, not Map.copyOf: the latter does not keep insertion order.
        return Collections.unmodifiableMap(signals);
    }

    /**
     * Scans content for known injection shapes.
     *
     * @param content sanitized untrusted content; {@code null} is treated as empty
     * @return the labels that matched, in a stable order — empty when nothing did. Never
     *         {@code null}, and never a reason to abort an analysis.
     */
    public List<String> scan(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, Pattern> signal : SIGNALS.entrySet()) {
            if (signal.getValue().matcher(content).find()) {
                matched.add(signal.getKey());
            }
        }
        return List.copyOf(matched);
    }
}
