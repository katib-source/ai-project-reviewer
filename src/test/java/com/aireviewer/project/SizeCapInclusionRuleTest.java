package com.aireviewer.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SizeCapInclusionRuleTest {

    @Test
    void includesFileUnderTheCap() {
        SizeCapInclusionRule rule = new SizeCapInclusionRule(1000L);

        assertTrue(rule.isFileIncluded(Path.of("Main.java"), 500L));
    }

    @Test
    void includesFileExactlyAtTheCap() {
        SizeCapInclusionRule rule = new SizeCapInclusionRule(1000L);

        assertTrue(rule.isFileIncluded(Path.of("Main.java"), 1000L));
    }

    @Test
    void excludesFileOverTheCap() {
        SizeCapInclusionRule rule = new SizeCapInclusionRule(1000L);

        assertFalse(rule.isFileIncluded(Path.of("bundle.js"), 1001L));
    }

    @Test
    void neverAffectsDirectories() {
        SizeCapInclusionRule rule = new SizeCapInclusionRule(1000L);

        assertTrue(rule.isDirectoryIncluded(Path.of("src")));
    }

    @Test
    void rejectsNegativeCap() {
        assertThrows(IllegalArgumentException.class, () -> new SizeCapInclusionRule(-1L));
    }
}
