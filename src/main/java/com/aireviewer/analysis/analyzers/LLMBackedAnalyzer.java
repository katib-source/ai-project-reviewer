package com.aireviewer.analysis.analyzers;

import com.aireviewer.analysis.AbstractAnalyzer;
import com.aireviewer.analysis.Criterion;
import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.llm.LLMEvaluation;
import com.aireviewer.llm.LLMException;
import com.aireviewer.llm.LLMProvider;
import com.aireviewer.llm.LLMRequest;
import com.aireviewer.llm.LLMResponse;
import com.aireviewer.llm.LLMResponseValidator;
import com.aireviewer.llm.PromptBuilder;
import com.aireviewer.llm.PromptCriterion;
import com.aireviewer.project.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

/**
 * Bridge between an {@link LLMProvider} and the {@code analysis} package's own {@link Criterion} /
 * {@link CriterionResult} shapes. Unlike {@link FileSizeStatisticsAnalyzer} and
 * {@link NamingConventionAnalyzer}, {@link Criterion} is a constructor parameter here, not a fixed
 * constant — per CLAUDE.md §7, a new LLM-backed criterion should mean constructing another instance
 * of this class with a different {@code Criterion}, "ideally no new class at all."
 * <p>
 * Takes {@link LLMProvider} by constructor injection and never constructs a concrete provider or
 * calls {@code LLMProviderFactory} itself — {@code Main} builds whichever (possibly
 * resilience-decorated) provider is configured and hands it in. This class is unaware of retry,
 * timeout or fallback happening underneath the injected provider.
 * <p>
 * {@link PromptBuilder} is injected too, since its settings (content cap, temperature, max output
 * tokens) are meant to come from the composition root, per that class's own Javadoc.
 * {@link LLMResponseValidator} is not: it has no configuration surface at all, so it is constructed
 * internally, the same way {@link PromptBuilder} constructs its own internal detector.
 */
public final class LLMBackedAnalyzer extends AbstractAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(LLMBackedAnalyzer.class);

    /**
     * The scale every LLM-backed criterion is asked to score on, matching the 0-10 scale the
     * deterministic analyzers already use so {@code AnalysisEngine}'s weighted average stays
     * comparable across every criterion. {@link LLMResponseValidator} deliberately enforces no
     * upper bound of its own ("this package has no business inventing a grading scale") — that
     * check happens here instead, see {@link #doAnalyze}.
     */
    private static final double PROMPT_MAX_SCORE = 10.0;

    private static final LLMResponseValidator VALIDATOR = new LLMResponseValidator();

    private final Criterion criterion;
    private final LLMProvider provider;
    private final PromptBuilder promptBuilder;

    /** Uses a default {@link PromptBuilder} — convenient when the composition root has no custom settings to pass. */
    public LLMBackedAnalyzer(Criterion criterion, LLMProvider provider) {
        this(criterion, provider, new PromptBuilder());
    }

    public LLMBackedAnalyzer(Criterion criterion, LLMProvider provider, PromptBuilder promptBuilder) {
        this.criterion = Objects.requireNonNull(criterion, "criterion");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
    }

    @Override
    public Criterion criterion() {
        return criterion;
    }

    @Override
    protected CriterionResult doAnalyze(FileNode projectRoot) {
        String projectContent = collectProjectContent(projectRoot);
        if (projectContent.isBlank()) {
            return CriterionResult.failed(criterion, "No file content was available to evaluate.");
        }

        PromptCriterion promptCriterion =
                new PromptCriterion(criterion.id(), criterion.name(), criterion.description(), PROMPT_MAX_SCORE);
        LLMRequest request = promptBuilder.build(promptCriterion, projectContent);

        LOG.info("Requesting LLM evaluation for criterion '{}' from provider '{}'",
                criterion.id(), provider.describe());
        try {
            LLMResponse response = provider.complete(request);
            LLMEvaluation evaluation = VALIDATOR.validate(response);

            if (evaluation.score() > PROMPT_MAX_SCORE) {
                LOG.warn("Provider '{}' returned a score of {} outside the requested 0-{} scale for criterion '{}'",
                        provider.describe(), evaluation.score(), PROMPT_MAX_SCORE, criterion.id());
                return CriterionResult.failed(criterion, "Model returned a score of " + evaluation.score()
                        + ", outside the requested 0-" + formatScale(PROMPT_MAX_SCORE) + " scale.");
            }

            LOG.info("Received LLM evaluation for criterion '{}' (score={}, fromFallback={})",
                    criterion.id(), evaluation.score(), evaluation.fromFallback());
            return CriterionResult.success(criterion, evaluation.score(), formatComment(evaluation));
        } catch (LLMException e) {
            return CriterionResult.failed(criterion, "LLM evaluation failed (" + e.kind() + "): " + e.getMessage());
        }
    }

    private static String formatComment(LLMEvaluation evaluation) {
        StringBuilder comment = new StringBuilder();
        appendSection(comment, "Strengths", evaluation.strengths());
        appendSection(comment, "Weaknesses", evaluation.weaknesses());
        appendSection(comment, "Recommendations", evaluation.recommendations());
        if (comment.isEmpty()) {
            comment.append("Model provided a score with no further explanation.");
        }
        if (evaluation.fromFallback()) {
            comment.append(" (evaluated by a fallback provider)");
        }
        return comment.toString().strip();
    }

    private static void appendSection(StringBuilder comment, String label, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        comment.append(label).append(": ").append(String.join("; ", items)).append("\n");
    }

    private static String formatScale(double maxScore) {
        return maxScore == Math.rint(maxScore) ? String.valueOf((long) maxScore) : String.valueOf(maxScore);
    }

    /**
     * Concatenates the readable text content of every file under {@code projectRoot}. A file that
     * fails to read is logged at WARN and skipped rather than aborting the whole criterion — with
     * potentially dozens of files feeding one aggregate prompt, losing one is a minor, routine
     * degradation, the same judgment {@code project.ProjectImporter} already makes about one
     * unreadable filesystem entry during import.
     */
    private String collectProjectContent(FileNode node) {
        StringBuilder content = new StringBuilder();
        appendContent(node, content);
        return content.toString();
    }

    private void appendContent(FileNode node, StringBuilder content) {
        if (node.isDirectory()) {
            for (FileNode child : node.children()) {
                appendContent(child, content);
            }
            return;
        }
        try {
            content.append("=== ").append(node.path()).append(" ===\n")
                    .append(Files.readString(node.path())).append("\n\n");
        } catch (IOException e) {
            LOG.warn("Skipping unreadable file '{}' while collecting content for criterion '{}'",
                    node.path(), criterion.id(), e);
        }
    }
}
