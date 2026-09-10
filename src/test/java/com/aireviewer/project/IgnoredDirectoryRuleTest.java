package com.aireviewer.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IgnoredDirectoryRuleTest {

    private static final Set<String> DEFAULT_IGNORES = Set.of("target", "node_modules", ".git");

    @Test
    void excludesDirectoryMatchingAnIgnoredName() {
        IgnoredDirectoryRule rule = new IgnoredDirectoryRule(DEFAULT_IGNORES);

        assertFalse(rule.isDirectoryIncluded(Path.of("project/target")));
        assertFalse(rule.isDirectoryIncluded(Path.of("project/node_modules")));
        assertFalse(rule.isDirectoryIncluded(Path.of("project/.git")));
    }

    @Test
    void includesDirectoryNotInIgnoreSet() {
        IgnoredDirectoryRule rule = new IgnoredDirectoryRule(DEFAULT_IGNORES);

        assertTrue(rule.isDirectoryIncluded(Path.of("project/src")));
    }

    @Test
    void matchesOnlyTheDirectoryOwnNameNotItsFullPath() {
        IgnoredDirectoryRule rule = new IgnoredDirectoryRule(DEFAULT_IGNORES);

        assertTrue(rule.isDirectoryIncluded(Path.of("target/subproject")));
    }

    @Test
    void neverAffectsFiles() {
        IgnoredDirectoryRule rule = new IgnoredDirectoryRule(DEFAULT_IGNORES);

        assertTrue(rule.isFileIncluded(Path.of("target/build.log"), 100L));
    }

    @Test
    void rejectsNullIgnoreSet() {
        assertThrows(NullPointerException.class, () -> new IgnoredDirectoryRule(null));
    }
}
