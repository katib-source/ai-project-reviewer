package com.aireviewer.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionInclusionRuleTest {

    @Test
    void includesFileWithAllowedExtension() {
        ExtensionInclusionRule rule = new ExtensionInclusionRule(Set.of("java", "md"));

        assertTrue(rule.isFileIncluded(Path.of("src/Main.java"), 100L));
    }

    @Test
    void matchesExtensionCaseInsensitively() {
        ExtensionInclusionRule rule = new ExtensionInclusionRule(Set.of("java"));

        assertTrue(rule.isFileIncluded(Path.of("src/Main.JAVA"), 100L));
    }

    @Test
    void excludesFileWithDisallowedExtension() {
        ExtensionInclusionRule rule = new ExtensionInclusionRule(Set.of("java"));

        assertFalse(rule.isFileIncluded(Path.of("image.png"), 100L));
    }

    @Test
    void excludesExtensionlessFileByDefault() {
        ExtensionInclusionRule rule = new ExtensionInclusionRule(Set.of("java"));

        assertFalse(rule.isFileIncluded(Path.of("Dockerfile"), 100L));
    }

    @Test
    void includesExtensionlessFileWhenEmptyStringIsAllowed() {
        ExtensionInclusionRule rule = new ExtensionInclusionRule(Set.of("java", ""));

        assertTrue(rule.isFileIncluded(Path.of("Dockerfile"), 100L));
    }

    @Test
    void neverAffectsDirectories() {
        ExtensionInclusionRule rule = new ExtensionInclusionRule(Set.of("java"));

        assertTrue(rule.isDirectoryIncluded(Path.of("src")));
    }

    @Test
    void rejectsNullAllowList() {
        assertThrows(NullPointerException.class, () -> new ExtensionInclusionRule(null));
    }
}
