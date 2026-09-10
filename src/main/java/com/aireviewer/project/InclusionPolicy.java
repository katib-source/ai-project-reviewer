package com.aireviewer.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Strategy pattern context. Holds an ordered list of {@link InclusionRule} strategies and
 * combines them with AND: a file or directory is included only if every configured rule agrees.
 * This is what lets the importer stay ignorant of how many rules are active or what they check —
 * it asks this one object two questions and gets one answer, regardless of whether that means
 * consulting zero rules or ten.
 * <p>
 * An empty rule list included is the identity case: with no rules to object, everything is
 * included, exactly as a single rule that never overrides either {@link InclusionRule} default
 * method would behave. Evaluation short-circuits on the first rejecting rule — remaining rules
 * are never consulted once one has already said no.
 */
final class InclusionPolicy {

    private final List<InclusionRule> rules;

    InclusionPolicy(List<InclusionRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    boolean isFileIncluded(Path path, long sizeInBytes) {
        for (InclusionRule rule : rules) {
            if (!rule.isFileIncluded(path, sizeInBytes)) {
                return false;
            }
        }
        return true;
    }

    boolean isDirectoryIncluded(Path path) {
        for (InclusionRule rule : rules) {
            if (!rule.isDirectoryIncluded(path)) {
                return false;
            }
        }
        return true;
    }
}
