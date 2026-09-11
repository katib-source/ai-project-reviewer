package com.aireviewer.analysis.analyzers;

import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.project.FileNode;
import com.aireviewer.security.MockSandboxRunner;
import com.aireviewer.security.SandboxResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildCheckAnalyzerTest {

    @Test
    void mavenProjectValidatesSuccessfully() {
        MockSandboxRunner sandbox = new MockSandboxRunner();
        sandbox.setNextResult(new SandboxResult(0, "", "", false));
        BuildCheckAnalyzer analyzer = new BuildCheckAnalyzer(sandbox);

        CriterionResult result = analyzer.analyze(directory("root", file("pom.xml")));

        assertTrue(result.successful());
        assertEquals(10.0, result.score(), 1e-9);
    }

    @Test
    void mavenProjectFailsValidation() {
        MockSandboxRunner sandbox = new MockSandboxRunner();
        sandbox.setNextResult(new SandboxResult(1, "", "invalid pom: missing groupId", false));
        BuildCheckAnalyzer analyzer = new BuildCheckAnalyzer(sandbox);

        CriterionResult result = analyzer.analyze(directory("root", file("pom.xml")));

        assertTrue(result.successful());
        assertEquals(0.0, result.score(), 1e-9);
        assertTrue(result.comment().contains("invalid pom: missing groupId"));
    }

    @Test
    void nodeProjectRunsANodeOnlyManifestCheck() {
        MockSandboxRunner sandbox = new MockSandboxRunner();
        sandbox.setNextResult(new SandboxResult(0, "", "", false));
        BuildCheckAnalyzer analyzer = new BuildCheckAnalyzer(sandbox);

        analyzer.analyze(directory("root", file("package.json")));

        List<String> command = sandbox.getLastCommand();
        assertEquals("node", command.get(0));
        assertTrue(String.join(" ", command).contains("package.json"));
    }

    @Test
    void bothManifestsPresentPrefersMaven() {
        MockSandboxRunner sandbox = new MockSandboxRunner();
        sandbox.setNextResult(new SandboxResult(0, "", "", false));
        BuildCheckAnalyzer analyzer = new BuildCheckAnalyzer(sandbox);

        analyzer.analyze(directory("root", file("pom.xml"), file("package.json")));

        assertEquals("mvn", sandbox.getLastCommand().get(0));
    }

    @Test
    void neitherManifestPresentNeverCallsTheSandbox() {
        MockSandboxRunner sandbox = new MockSandboxRunner();
        BuildCheckAnalyzer analyzer = new BuildCheckAnalyzer(sandbox);

        CriterionResult result = analyzer.analyze(directory("root", file("README.md")));

        assertFalse(result.successful());
        assertEquals(0, sandbox.getCallCount());
    }

    @Test
    void aSandboxTimeoutIsInconclusiveNotAFailure() {
        MockSandboxRunner sandbox = new MockSandboxRunner();
        sandbox.setNextResult(new SandboxResult(-1, "", "", true));
        BuildCheckAnalyzer analyzer = new BuildCheckAnalyzer(sandbox);

        CriterionResult result = analyzer.analyze(directory("root", file("pom.xml")));

        assertFalse(result.successful());
        assertTrue(result.comment().contains("timed out"));
    }

    @Test
    void anInternalSandboxErrorIsInconclusiveNotAFailure() {
        MockSandboxRunner sandbox = new MockSandboxRunner();
        sandbox.setNextResult(new SandboxResult(-1, "", "docker: command not found", false));
        BuildCheckAnalyzer analyzer = new BuildCheckAnalyzer(sandbox);

        CriterionResult result = analyzer.analyze(directory("root", file("pom.xml")));

        assertFalse(result.successful());
        assertTrue(result.comment().contains("docker: command not found"));
    }

    @Test
    void passesTheProjectRootPathToTheSandbox() {
        MockSandboxRunner sandbox = new MockSandboxRunner();
        sandbox.setNextResult(new SandboxResult(0, "", "", false));
        BuildCheckAnalyzer analyzer = new BuildCheckAnalyzer(sandbox);
        FileNode root = directory("root", file("pom.xml"));

        analyzer.analyze(root);

        assertEquals(root.path(), sandbox.getLastProjectDir());
    }

    private static FileNode file(String name) {
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
                return Path.of("/projects/" + name);
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
