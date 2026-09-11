package com.aireviewer.analysis.analyzers;

import com.aireviewer.analysis.AbstractAnalyzer;
import com.aireviewer.analysis.Criterion;
import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.project.FileNode;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic criterion: checks that every Java type declaration (class, interface, enum,
 * record) in the project uses UpperCamelCase naming. Deliberately narrow — method, field and
 * constant naming are different rules with different reliable-detection shapes, and bundling them
 * here would need "and" to describe (CLAUDE.md §8).
 * <p>
 * Detection is a regex heuristic, not a real parser: a string literal or comment that happens to
 * contain the literal sequence {@code "class Foo"} could in principle produce a false positive.
 * Accepted for the same reason the subject gives for the whole project — the architecture is what
 * is being assessed, not how bulletproof one regex is.
 * <p>
 * Unlike {@code project.ProjectImporter}, which deliberately treats one unreadable filesystem
 * entry as routine and skips it (its own Javadoc: "one unreadable entry never aborts the
 * import"), this class lets an {@link IOException} from reading a file's content propagate rather
 * than catching and skipping it. That is not an inconsistency: every {@link FileNode} reaching
 * this class was already successfully imported, so a content read failing now means the file
 * changed or vanished between import and analysis — a rare race, not routine noise — worth
 * surfacing as a failed criterion via {@link AbstractAnalyzer}'s existing exception handling
 * rather than silently under-reporting a partial score.
 */
public final class NamingConventionAnalyzer extends AbstractAnalyzer {

    private static final Criterion CRITERION = new Criterion(
            "naming-conventions",
            "Naming Conventions",
            "Checks that Java type declarations (classes, interfaces, enums, records) follow "
                    + "UpperCamelCase naming.",
            1.0
    );

    private static final Pattern TYPE_DECLARATION =
            Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern UPPER_CAMEL_CASE = Pattern.compile("^[A-Z][A-Za-z0-9]*$");

    @Override
    public Criterion criterion() {
        return CRITERION;
    }

    @Override
    protected CriterionResult doAnalyze(FileNode projectRoot) throws IOException {
        List<FileNode> javaFiles = new ArrayList<>();
        collectJavaFiles(projectRoot, javaFiles);

        if (javaFiles.isEmpty()) {
            return CriterionResult.failed(CRITERION,
                    "No Java source files were available to check naming conventions.");
        }

        int typeCount = 0;
        int violationCount = 0;
        for (FileNode file : javaFiles) {
            String content = Files.readString(file.path());
            Matcher matcher = TYPE_DECLARATION.matcher(content);
            while (matcher.find()) {
                typeCount++;
                if (!UPPER_CAMEL_CASE.matcher(matcher.group(1)).matches()) {
                    violationCount++;
                }
            }
        }

        if (typeCount == 0) {
            return CriterionResult.failed(CRITERION,
                    "No type declarations were found to check naming conventions.");
        }

        double violationFraction = violationCount / (double) typeCount;
        double score = 10.0 * (1.0 - violationFraction);

        String comment = String.format(
                "%d type declaration(s) found across %d file(s); %d (%.0f%%) do not follow "
                        + "UpperCamelCase naming.",
                typeCount, javaFiles.size(), violationCount, violationFraction * 100
        );

        return CriterionResult.success(CRITERION, score, comment);
    }

    private static void collectJavaFiles(FileNode node, List<FileNode> javaFiles) {
        if (node.isDirectory()) {
            for (FileNode child : node.children()) {
                collectJavaFiles(child, javaFiles);
            }
        } else if (node.name().endsWith(".java")) {
            javaFiles.add(node);
        }
    }
}
