package com.aireviewer.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration Docker.
 *
 * Ces tests nécessitent :
 *   - Docker installé et lancé
 *   - l'image "sandbox-image" construite
 *   - la variable d'environnement RUN_DOCKER_IT=true
 *
 * Ils ne s'exécutent PAS avec `mvn test` par défaut.
 * Pour les lancer :
 *   $env:RUN_DOCKER_IT="true"; mvn test -Dtest=DockerSandboxRunnerIT
 */
@EnabledIfEnvironmentVariable(named = "RUN_DOCKER_IT", matches = "true")
class DockerSandboxRunnerIT {

    @Test
    void run_executes_simple_command_in_docker() {
        Path projectDir = Path.of(".").toAbsolutePath();
        DockerSandboxRunner runner = new DockerSandboxRunner("sandbox-image");

        SandboxResult result = runner.run(
            projectDir,
            List.of("ls", "-la", "/projet"),
            Duration.ofSeconds(30)
        );

        assertFalse(result.timedOut(), "Should not timeout");
        assertEquals(0, result.exitCode(), "Should exit with code 0");
    }

    @Test
    void run_returns_timeout_when_command_too_long() {
        Path projectDir = Path.of(".").toAbsolutePath();
        DockerSandboxRunner runner = new DockerSandboxRunner("sandbox-image");

        SandboxResult result = runner.run(
            projectDir,
            List.of("sleep", "60"),
            Duration.ofSeconds(2)
        );

        assertTrue(result.timedOut(), "Should timeout");
        assertEquals(-1, result.exitCode());
    }
}