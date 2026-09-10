package com.aireviewer.analysis;

import com.aireviewer.project.FileNode;

/**
 * Strategy pattern: one evaluation algorithm per implementation (file/size statistics, naming
 * conventions, an LLM-backed check, ...), so {@code AnalysisEngine} can run an open-ended,
 * configurable selection of them without knowing what any individual one actually checks. Adding a
 * new criterion is exactly one new class implementing this interface (or, for an LLM-backed one,
 * often no new class at all — see CLAUDE.md §7); nothing about {@code AnalysisEngine} changes.
 * <p>
 * {@link #analyze} takes the imported project as a {@link FileNode} — the {@code project} package
 * deliberately keeps its {@code DirectoryNode} implementation package-private, so {@link FileNode}
 * (its public Composite root, whose own Javadoc names analyzers as an intended caller) is the only
 * type this package is able to reference anyway.
 * <p>
 * {@link #analyze} is expected never to throw. That guarantee isn't something this interface can
 * enforce by itself — implementations should extend {@link AbstractAnalyzer} rather than
 * implementing this interface directly, since it's what actually turns a thrown exception into a
 * {@link CriterionResult#failed}. That's what lets {@code AnalysisEngine} treat "one analyzer's bug
 * can never crash the whole run" as a real guarantee, not just a convention every analyzer has to
 * remember to follow on its own.
 */
public interface Analyzer {

    /** The criterion this analyzer evaluates. */
    Criterion criterion();

    /** Evaluates {@code projectRoot} against {@link #criterion()} and returns the outcome. */
    CriterionResult analyze(FileNode projectRoot);
}
