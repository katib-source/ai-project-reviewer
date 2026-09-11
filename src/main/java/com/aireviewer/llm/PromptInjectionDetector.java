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
 * instructions, and will light up several signals. That is why nothing branches on the result. The
 * French and Chinese alternations inherit exactly that looseness and no more: a comment reading
 * "on oublie toutes les regles de nommage" flags, and so does its English equivalent "forget all the
 * naming rules". Tightening French to imperative verb forms only would cut those, at the cost of
 * missing a real attack written in the second person — the wrong trade for an advisory signal.
 *
 * <p>These findings could later feed the security criterion's report section ("the analyzed project
 * contains text that attempts to manipulate an automated reviewer"), which would turn an attack into
 * a genuine finding about the project. Not wired up — the analysis side owns that decision.
 *
 * <p>Scanning happens <em>after</em> {@link PromptBuilder} sanitizes the content, so zero-width
 * characters and homoglyphs cannot hide a phrase from these patterns.
 *
 * <p>{@code instruction-override} and {@code role-change} also match French and Chinese phrasings,
 * those being the two shapes an attacker gets for free by running an English attempt through a
 * translator. The other three labels stay English-only, and that is the honest limit of a keyword
 * scanner: it will always be one language and one paraphrase behind. It is exactly why
 * {@link PromptBuilder} states the cross-lingual rule in the prompt instead of relying on
 * detection — a translated injection this class misses is still structurally contained.
 *
 * <p>Stateless and thread-safe.
 */
public final class PromptInjectionDetector {

    // Non-ASCII literals are written as escapes so no encoding mishap can silently break a pattern.
    // These are compile-time constants, so declaration order relative to SIGNALS does not matter.

    /** e-acute, as in "precedentes". */
    private static final String E_ACUTE = "\u00e9";
    /** e-grave, as in "regles". */
    private static final String E_GRAVE = "\u00e8";
    /** "etes" with e-circumflex, as in "vous etes maintenant". */
    private static final String E_GRAVE_TES = "\u00eates";
    /** a-grave, as in "a partir de maintenant". */
    private static final String A_GRAVE = "\u00e0";
    /** o-circumflex, as in "role". */
    private static final String O_CIRC = "\u00f4";

    /** Chinese "ignore" (hu lue). */
    private static final String ZH_IGNORE = "\u5ffd\u7565";
    /** Chinese "disregard", simplified (wu shi). */
    private static final String ZH_DISREGARD = "\u65e0\u89c6";
    /** Chinese "disregard", traditional. */
    private static final String ZH_DISREGARD_TRAD = "\u7121\u8996";
    /** Chinese "forget" (wang ji). */
    private static final String ZH_FORGET = "\u5fd8\u8bb0";
    /** Chinese "instruction" (zhi ling). */
    private static final String ZH_INSTRUCTION = "\u6307\u4ee4";
    /** Chinese "directive" (zhi shi). */
    private static final String ZH_DIRECTIVE = "\u6307\u793a";
    /** Chinese "command" (ming ling). */
    private static final String ZH_COMMAND = "\u547d\u4ee4";
    /** Chinese "prompt" (ti shi). */
    private static final String ZH_PROMPT = "\u63d0\u793a";
    /** Chinese "rules", simplified (gui ze). */
    private static final String ZH_RULES = "\u89c4\u5219";
    /** Chinese "rules", traditional. */
    private static final String ZH_RULES_TRAD = "\u898f\u5247";
    /** Chinese "you are now" (ni xian zai shi). */
    private static final String ZH_YOU_ARE_NOW = "\u4f60\u73b0\u5728\u662f";
    /** Chinese "from now on" (cong xian zai kai shi). */
    private static final String ZH_FROM_NOW_ON = "\u4ece\u73b0\u5728\u5f00\u59cb";
    /** Chinese "pretend" (jia zhuang). */
    private static final String ZH_PRETEND = "\u5047\u88c5";
    /** Chinese "act as / play the role of" (ban yan). */
    private static final String ZH_ACT_AS = "\u626e\u6f14";

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
        String gap = "[^\\n]{0,30}";
        String verb = "\\b(?:ignore|disregard|forget|override|bypass)\\b";
        String qualifier = "\\b(?:previous|prior|earlier|above|preceding|all)\\b";
        String noun = "\\b(?:instruction|instructions|prompt|prompts|rule|rules|direction|directions)\\b";
        String english = verb + gap + "(?:" + qualifier + gap + noun + "|" + noun + gap + qualifier + ")";

        // French, the same verb-qualifier-noun shape. Accents are optional inside the character
        // classes: an attacker types whatever their keyboard gives them, and the sanitizer's NFKC
        // pass folds width and compatibility forms but deliberately leaves accents alone.
        String frVerb = "(?:ignore[rz]?|oubli(?:e|ez|er)|n[e" + E_ACUTE + "]glige[rz]?"
                + "|ne tiens pas compte)";
        String frQualifier = "(?:pr[e" + E_ACUTE + "]c[e" + E_ACUTE + "]dentes?|ci-dessus"
                + "|ant[e" + E_ACUTE + "]rieures?|toutes?|tous)";
        String frNoun = "(?:instructions?|consignes?|r[e" + E_GRAVE + "]gles?|directives?)";
        String french = frVerb + gap + "(?:" + frQualifier + gap + frNoun + "|"
                + frNoun + gap + frQualifier + ")";

        // Chinese carries no word boundaries that \\b can see - \\b is defined in terms of \\w, which
        // does not apply to these scripts - so this is verb followed by noun within a short span,
        // which is how the phrasing works anyway: "ignore / all the above / instructions".
        String chinese = "(?:" + ZH_IGNORE + "|" + ZH_DISREGARD + "|" + ZH_DISREGARD_TRAD + "|"
                + ZH_FORGET + ")[^\\n]{0,10}(?:" + ZH_INSTRUCTION + "|" + ZH_DIRECTIVE + "|"
                + ZH_COMMAND + "|" + ZH_PROMPT + "|" + ZH_RULES + "|" + ZH_RULES_TRAD + ")";

        // (?u) so case folding reaches accented letters; ASCII folding is unaffected by it.
        signals.put("instruction-override", Pattern.compile(
                "(?iu)(?:" + english + "|" + french + "|" + chinese + ")"));

        // Text pretending to be a new conversational turn or a system prompt. The line-start
        // anchor tolerates comment markers, because the usual hiding place is a code comment:
        // "// SYSTEM:", "# SYSTEM:", " * SYSTEM:".
        signals.put("fake-system-turn", Pattern.compile(
                "(?im)^[\\s/*#>|-]{0,6}(system|assistant|developer)\\s*:"
                        + "|<\\|im_(start|end)\\|>"
                        + "|#{2,}\\s*(system|instructions?)\\s*#{2,}"
                        + "|\\[\\s*(system|system prompt)\\s*\\]"));

        // Attempts to reassign the model's role, in English, French and Chinese.
        String enRole = "\\byou are now\\b"
                + "|\\bfrom now on\\b[^\\n]{0,25}\\byou\\b"
                + "|\\bpretend to be\\b"
                + "|\\bact as\\b[^\\n]{0,20}\\b(?:assistant|model|reviewer|grader|evaluator)\\b"
                + "|\\byour new (?:role|task|instructions?)\\b";
        String frRole = "|(?:tu es|vous " + E_GRAVE_TES + ")\\s+maintenant"
                + "|d[e" + E_ACUTE + "]sormais,?\\s+(?:tu|vous)\\b"
                + "|" + A_GRAVE + " partir de maintenant,?\\s+(?:tu|vous)\\b"
                + "|\\bagis(?:sez)? comme\\b"
                + "|\\bfais(?:-|\\s)comme si\\b"
                + "|\\b(?:ton|votre) nouveau r[o" + O_CIRC + "]le\\b";
        String zhRole = "|" + ZH_YOU_ARE_NOW
                + "|" + ZH_FROM_NOW_ON
                + "|" + ZH_PRETEND
                + "|" + ZH_ACT_AS;
        signals.put("role-change", Pattern.compile("(?iu)" + enRole + frRole + zhRole));

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
