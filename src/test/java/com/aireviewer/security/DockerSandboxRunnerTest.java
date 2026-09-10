package com.aireviewer.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DockerSandboxRunnerTest {

    @Test
    void run_returns_success_for_simple_command() {
        // Nécessite Docker et l'image sandbox-image.
        // À exécuter manuellement, pas dans la CI.
        Path projectDir = Path.of("C:/Users/Lenovo/projet-test");
        DockerSandboxRunner runner = new DockerSandboxRunner("sandbox-image");

        SandboxResult result = runner.run(
            projectDir,
            List.of("ls", "-la", "/projet"),
            Duration.ofSeconds(30)
        );

        assertFalse(result.timedOut());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("README.md"));
    }

    @Test
    void run_returns_timeout_when_command_too_long() {
        Path projectDir = Path.of("C:/Users/Lenovo/projet-test");
        DockerSandboxRunner runner = new DockerSandboxRunner("sandbox-image");

        SandboxResult result = runner.run(
            projectDir,
            List.of("sleep", "60"),
            Duration.ofSeconds(2)
        );

        assertTrue(result.timedOut());
        assertEquals(-1, result.exitCode());
    }

    @Test
    void mock_runner_returns_configured_result() {
        MockSandboxRunner mock = new MockSandboxRunner();
        mock.setNextResult(new SandboxResult(1, "out", "err", false));

        SandboxResult result = mock.run(
            Path.of("/tmp"),
            List.of("echo", "hi"),
            Duration.ofSeconds(5)
        );

        assertEquals(1, result.exitCode());
        assertEquals("out", result.stdout());
        assertEquals("err", result.stderr());
        assertEquals(1, mock.getCallCount());
    }
}
