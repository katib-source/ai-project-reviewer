package com.aireviewer.project;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Strategy pattern concrete strategy. Excludes a directory whose own name (not full path) matches
 * one of a configured set — e.g. {@code target}, {@code node_modules}, {@code .git} — so the
 * importer never descends into build output, dependency caches or VCS internals, regardless of
 * how deep they appear in the tree. Files are unaffected: this rule only answers
 * {@link #isDirectoryIncluded}.
 */
final class IgnoredDirectoryRule implements InclusionRule {

    private final Set<String> ignoredNames;

    IgnoredDirectoryRule(Set<String> ignoredNames) {
        Objects.requireNonNull(ignoredNames, "ignoredNames");
        this.ignoredNames = Set.copyOf(ignoredNames);
    }

    @Override
    public boolean isDirectoryIncluded(Path path) {
        return !ignoredNames.contains(path.getFileName().toString());
    }
}
