package com.aireviewer.analysis.analyzers;

import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.project.FileNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingConventionAnalyzerTest {

    private final NamingConventionAnalyzer analyzer = new NamingConventionAnalyzer();

    @Test
    void scoresTenWhenEveryTypeNameIsUpperCamelCase(@TempDir Path tempDir) throws IOException {
        Path source = writeFile(tempDir, "Good.java", "public class Good { interface Nested {} }");

        CriterionResult result = analyzer.analyze(directory("root", file(source)));

        assertTrue(result.successful());
        assertEquals(10.0, result.score(), 1e-9);
    }

    @Test
    void countsBadlyNamedTypesAsViolations(@TempDir Path tempDir) throws IOException {
        Path source = writeFile(tempDir, "Mixed.java",
                "public class Good {}\n"
                        + "interface bad {}\n"
                        + "enum AnotherGood {}\n"
                        + "record badRecord(int x) {}\n");

        CriterionResult result = analyzer.analyze(directory("root", file(source)));

        assertEquals(5.0, result.score(), 1e-9);
        assertTrue(result.comment().contains("4 type declaration(s)"));
        assertTrue(result.comment().contains("2 (50%)"));
    }

    @Test
    void recursesIntoNestedDirectories(@TempDir Path tempDir) throws IOException {
        Path nestedDir = Files.createDirectory(tempDir.resolve("nested"));
        Path top = writeFile(tempDir, "Top.java", "class Top {}");
        Path nested = writeFile(nestedDir, "Nested.java", "class Nested {}");

        CriterionResult result = analyzer.analyze(directory("root", file(top), directory("nested", file(nested))));

        assertTrue(result.comment().contains("2 type declaration(s)"));
    }

    @Test
    void returnsAFailedResultWhenNoJavaFilesArePresent(@TempDir Path tempDir) throws IOException {
        Path notJava = writeFile(tempDir, "readme.txt", "class Foo {}");

        CriterionResult result = analyzer.analyze(directory("root", file(notJava)));

        assertFalse(result.successful());
    }

    @Test
    void returnsAFailedResultWhenNoTypeDeclarationsAreFound(@TempDir Path tempDir) throws IOException {
        Path packageInfo = writeFile(tempDir, "package-info.java", "/** Package doc. */\npackage com.example;\n");

        CriterionResult result = analyzer.analyze(directory("root", file(packageInfo)));

        assertFalse(result.successful());
    }

    @Test
    void convertsARealIOExceptionIntoAFailedResultRatherThanThrowing(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("Missing.java");

        CriterionResult result = analyzer.analyze(directory("root", file(missing)));

        assertFalse(result.successful());
        assertEquals(0.0, result.score());
    }

    private static Path writeFile(Path directory, String name, String content) throws IOException {
        Path path = directory.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private static FileNode file(Path path) {
        return new FileNode() {
            @Override
            public String name() {
                return path.getFileName().toString();
            }

            @Override
            public Path path() {
                return path;
            }

            @Override
            public long sizeInBytes() {
                return 0;
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
                return 0;
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
