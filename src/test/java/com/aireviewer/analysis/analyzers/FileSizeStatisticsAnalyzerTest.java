package com.aireviewer.analysis.analyzers;

import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.project.FileNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSizeStatisticsAnalyzerTest {

    @Test
    void scoresTenWhenNoFileExceedsTheThreshold() {
        FileSizeStatisticsAnalyzer analyzer = new FileSizeStatisticsAnalyzer(100);
        FileNode root = directory("root", file("a", 10), file("b", 90));

        CriterionResult result = analyzer.analyze(root);

        assertTrue(result.successful());
        assertEquals(10.0, result.score(), 1e-9);
    }

    @Test
    void scoresZeroWhenEveryFileExceedsTheThreshold() {
        FileSizeStatisticsAnalyzer analyzer = new FileSizeStatisticsAnalyzer(10);
        FileNode root = directory("root", file("a", 50), file("b", 90));

        CriterionResult result = analyzer.analyze(root);

        assertTrue(result.successful());
        assertEquals(0.0, result.score(), 1e-9);
    }

    @Test
    void scoresProportionallyToTheFractionOfLargeFiles() {
        FileSizeStatisticsAnalyzer analyzer = new FileSizeStatisticsAnalyzer(100);
        FileNode root = directory("root", file("a", 50), file("b", 150), file("c", 250));

        CriterionResult result = analyzer.analyze(root);

        assertEquals(10.0 * (1.0 - 2.0 / 3.0), result.score(), 1e-9);
        assertTrue(result.comment().contains("3 file(s)"));
        assertTrue(result.comment().contains("2 file(s)"));
    }

    @Test
    void recursesIntoNestedDirectories() {
        FileSizeStatisticsAnalyzer analyzer = new FileSizeStatisticsAnalyzer(1000);
        FileNode nested = directory("nested", file("c", 20));
        FileNode root = directory("root", file("a", 10), nested);

        CriterionResult result = analyzer.analyze(root);

        assertTrue(result.comment().contains("2 file(s)"));
    }

    @Test
    void returnsAFailedResultWhenNoFilesAreAvailable() {
        FileSizeStatisticsAnalyzer analyzer = new FileSizeStatisticsAnalyzer(100);
        FileNode root = directory("root");

        CriterionResult result = analyzer.analyze(root);

        assertFalse(result.successful());
    }

    @Test
    void rejectsANegativeThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new FileSizeStatisticsAnalyzer(-1));
    }

    private static FileNode file(String name, long sizeInBytes) {
        return new FileNode() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Path path() {
                return Path.of(name);
            }

            @Override
            public long sizeInBytes() {
                return sizeInBytes;
            }

            @Override
            public boolean isDirectory() {
                return false;
            }

            @Override
            public List<FileNode> children() {
                return List.of();
            }
        };
    }

    private static FileNode directory(String name, FileNode... children) {
        List<FileNode> childList = List.of(children);
        return new FileNode() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Path path() {
                return Path.of(name);
            }

            @Override
            public long sizeInBytes() {
                long total = 0L;
                for (FileNode child : childList) {
                    total += child.sizeInBytes();
                }
                return total;
            }

            @Override
            public boolean isDirectory() {
                return true;
            }

            @Override
            public List<FileNode> children() {
                return childList;
            }
        };
    }
}
