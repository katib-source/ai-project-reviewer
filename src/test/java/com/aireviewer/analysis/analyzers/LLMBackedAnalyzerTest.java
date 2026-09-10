package com.aireviewer.analysis.analyzers;

import com.aireviewer.analysis.Criterion;
import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.llm.LLMException;
import com.aireviewer.llm.LLMProvider;
import com.aireviewer.llm.LLMRequest;
import com.aireviewer.llm.LLMResponse;
import com.aireviewer.llm.MockLLMProvider;
import com.aireviewer.project.FileNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMBackedAnalyzerTest {

    private static final Criterion CRITERION =
            new Criterion("design-quality", "Design Quality", "Checks overall design quality.", 1.0);

    @Test
    void scoresSuccessfullyWithinTheRequestedScale() {
        LLMProvider provider = respondingWith(
                "{\"criterion\":\"design-quality\",\"score\":8,"
                        + "\"strengths\":[\"Good separation\"],\"weaknesses\":[],"
                        + "\"recommendations\":[\"Add more tests\"]}",
                false);
        LLMBackedAnalyzer analyzer = new LLMBackedAnalyzer(CRITERION, provider);

        CriterionResult result = analyzer.analyze(directory("root", file("A.java", "class A {}")));

        assertTrue(result.successful());
        assertEquals(8.0, result.score(), 1e-9);
        assertEquals(CRITERION, result.criterion());
        assertTrue(result.comment().contains("Strengths: Good separation"));
        assertTrue(result.comment().contains("Recommendations: Add more tests"));
        assertFalse(result.comment().contains("Weaknesses"));
    }

    @Test
    void treatsAScoreOutsideTheRequestedScaleAsFailed() {
        // MockLLMProvider's placeholder answers 14 out of its own 20-point example scale, which
        // exceeds the 0-10 scale this bridge requests — exercising the upper-bound check
        // LLMResponseValidator deliberately leaves to the caller.
        LLMBackedAnalyzer analyzer = new LLMBackedAnalyzer(CRITERION, new MockLLMProvider());

        CriterionResult result = analyzer.analyze(directory("root", file("A.java", "class A {}")));

        assertFalse(result.successful());
        assertTrue(result.comment().contains("outside the requested 0-10 scale"));
    }

    @Test
    void convertsAnLLMExceptionIntoAFailedResultWithItsKind() {
        LLMBackedAnalyzer analyzer = new LLMBackedAnalyzer(CRITERION, MockLLMProvider.alwaysFailing());

        CriterionResult result = analyzer.analyze(directory("root", file("A.java", "class A {}")));

        assertFalse(result.successful());
        assertTrue(result.comment().contains("UNAVAILABLE"));
    }

    @Test
    void convertsAMalformedResponseIntoAFailedResult() {
        LLMProvider provider = respondingWith("not json at all", false);
        LLMBackedAnalyzer analyzer = new LLMBackedAnalyzer(CRITERION, provider);

        CriterionResult result = analyzer.analyze(directory("root", file("A.java", "class A {}")));

        assertFalse(result.successful());
        assertTrue(result.comment().contains("MALFORMED_RESPONSE"));
    }

    @Test
    void notesWhenTheEvaluationCameFromAFallbackProvider() {
        LLMProvider provider = respondingWith("{\"score\":5}", true);
        LLMBackedAnalyzer analyzer = new LLMBackedAnalyzer(CRITERION, provider);

        CriterionResult result = analyzer.analyze(directory("root", file("A.java", "class A {}")));

        assertTrue(result.successful());
        assertTrue(result.comment().contains("(evaluated by a fallback provider)"));
    }

    @Test
    void returnsAFailedResultWithoutCallingTheProviderWhenNoContentIsAvailable() {
        LLMBackedAnalyzer analyzer = new LLMBackedAnalyzer(CRITERION, failingIfCalled());

        CriterionResult result = analyzer.analyze(directory("root"));

        assertFalse(result.successful());
        assertTrue(result.comment().contains("No file content was available to evaluate."));
    }

    @Test
    void skipsAnUnreadableFileWhileStillSendingTheReadableOnes(@TempDir Path tempDir) throws IOException {
        Path readable = tempDir.resolve("Readable.java");
        Files.writeString(readable, "class Readable { /* Hello World */ }");
        Path missing = tempDir.resolve("Missing.java");

        CapturingProvider provider = new CapturingProvider();
        LLMBackedAnalyzer analyzer = new LLMBackedAnalyzer(CRITERION, provider);

        CriterionResult result = analyzer.analyze(directory("root", filePath(readable), filePath(missing)));

        assertTrue(result.successful());
        assertTrue(provider.lastRequest.untrustedContent().contains("Hello World"));
    }

    private static LLMProvider respondingWith(String jsonContent, boolean fromFallback) {
        return new LLMProvider() {
            @Override
            public LLMResponse complete(LLMRequest request) {
                return new LLMResponse(jsonContent, "stub-model", fromFallback);
            }

            @Override
            public String describe() {
                return "stub";
            }
        };
    }

    private static LLMProvider failingIfCalled() {
        return new LLMProvider() {
            @Override
            public LLMResponse complete(LLMRequest request) {
                throw new AssertionError("provider should not have been called");
            }

            @Override
            public String describe() {
                return "should-not-be-called";
            }
        };
    }

    private static final class CapturingProvider implements LLMProvider {
        private LLMRequest lastRequest;

        @Override
        public LLMResponse complete(LLMRequest request) {
            this.lastRequest = request;
            return new LLMResponse("{\"score\":5}", "stub-model", false);
        }

        @Override
        public String describe() {
            return "capturing";
        }
    }

    /** Backed by a real temporary file, since {@link LLMBackedAnalyzer} genuinely reads file content. */
    private static FileNode file(String name, String content) {
        try {
            Path path = Files.createTempFile("llm-backed-analyzer-test-", "-" + name);
            Files.writeString(path, content);
            path.toFile().deleteOnExit();
            return filePath(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static FileNode filePath(Path path) {
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
