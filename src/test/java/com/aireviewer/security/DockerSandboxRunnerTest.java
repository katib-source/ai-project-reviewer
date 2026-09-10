package com.aireviewer.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires du sandbox.
 * Ne dépendent NI de Docker, NI d'un chemin local.
 * Doivent passer sur n'importe quelle machine, même sans Docker installé.
 */
class DockerSandboxRunnerTest {

    @Test
    void mock_returns_configured_result() {
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

    @Test
    void mock_records_last_parameters() {
        MockSandboxRunner mock = new MockSandboxRunner();
        Path dir = Path.of("/chemin/test");
        List<String> cmd = List.of("ls", "-la");

        mock.run(dir, cmd, Duration.ofSeconds(5));

        assertEquals(dir, mock.getLastProjectDir());
        assertEquals(cmd, mock.getLastCommand());
        assertEquals(Duration.ofSeconds(5), mock.getLastTimeout());
    }

    @Test
    void mock_counts_calls() {
        MockSandboxRunner mock = new MockSandboxRunner();
        mock.run(Path.of("/a"), List.of("cmd1"), Duration.ofSeconds(1));
        mock.run(Path.of("/b"), List.of("cmd2"), Duration.ofSeconds(2));
        assertEquals(2, mock.getCallCount());
    }

    @Test
    void sandbox_result_is_success_when_exit_code_zero_and_not_timed_out() {
        SandboxResult ok = new SandboxResult(0, "out", "", false);
        SandboxResult fail = new SandboxResult(1, "", "err", false);
        SandboxResult timeout = new SandboxResult(-1, "", "", true);

        assertTrue(ok.isSuccess());
        assertFalse(fail.isSuccess());
        assertFalse(timeout.isSuccess());
    }
}