package com.aireviewer.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InclusionPolicyTest {

    private static final Path SOME_FILE = Path.of("src/Main.java");
    private static final Path SOME_DIR = Path.of("src");

    /** Always rejects, so a policy holding it must reject too. */
    private static final class RejectingRule implements InclusionRule {
        @Override
        public boolean isFileIncluded(Path path, long sizeInBytes) {
            return false;
        }

        @Override
        public boolean isDirectoryIncluded(Path path) {
            return false;
        }
    }

    /** Fails the test if invoked at all — placed after a rejecting rule to prove short-circuit. */
    private static final class ExplodingRule implements InclusionRule {
        @Override
        public boolean isFileIncluded(Path path, long sizeInBytes) {
            throw new AssertionError("isFileIncluded should not be called after an earlier rejection");
        }

        @Override
        public boolean isDirectoryIncluded(Path path) {
            throw new AssertionError("isDirectoryIncluded should not be called after an earlier rejection");
        }
    }

    @Test
    void emptyPolicyIncludesEverything() {
        InclusionPolicy policy = new InclusionPolicy(List.of());

        assertTrue(policy.isFileIncluded(SOME_FILE, 100L));
        assertTrue(policy.isDirectoryIncluded(SOME_DIR));
    }

    @Test
    void includesOnlyWhenEveryRuleAgrees() {
        InclusionPolicy allAccept = new InclusionPolicy(List.of(new InclusionRule() {}, new InclusionRule() {}));
        InclusionPolicy oneRejects = new InclusionPolicy(List.of(new InclusionRule() {}, new RejectingRule()));

        assertTrue(allAccept.isFileIncluded(SOME_FILE, 100L));
        assertTrue(allAccept.isDirectoryIncluded(SOME_DIR));
        assertFalse(oneRejects.isFileIncluded(SOME_FILE, 100L));
        assertFalse(oneRejects.isDirectoryIncluded(SOME_DIR));
    }

    @Test
    void shortCircuitsOnFirstRejectingRule() {
        InclusionPolicy policy = new InclusionPolicy(List.of(new RejectingRule(), new ExplodingRule()));

        assertFalse(policy.isFileIncluded(SOME_FILE, 100L));
        assertFalse(policy.isDirectoryIncluded(SOME_DIR));
    }

    @Test
    void rejectsNullRuleList() {
        assertThrows(NullPointerException.class, () -> new InclusionPolicy(null));
    }
}
