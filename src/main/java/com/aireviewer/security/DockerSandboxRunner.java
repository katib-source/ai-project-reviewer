package com.aireviewer.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DockerSandboxRunner implements SandboxRunner {

    private final String imageName;

    public DockerSandboxRunner(String imageName) {
        this.imageName = imageName;
    }

    @Override
    public SandboxResult run(Path projectDir, List<String> command, Duration timeout) {
        List<String> args = new ArrayList<>(List.of(
            "docker", "run", "--rm",
            "--network", "none",
            "--read-only",
            "--tmpfs", "/tmp",
            "--user", "1000:1000",
            "--memory", "512m",
            "--cpus", "1",
            "--pids-limit", "128",
            "--cap-drop", "ALL",
            "--security-opt", "no-new-privileges",
            "-v", projectDir.toAbsolutePath() + ":/projet:ro",
            imageName
        ));
        args.addAll(command);

        ProcessBuilder pb = new ProcessBuilder(args);

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return new SandboxResult(-1, "", "Timeout after " + timeout, true);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            return new SandboxResult(process.exitValue(), stdout, stderr, false);

        } catch (IOException e) {
            return new SandboxResult(-1, "", "IO error: " + e.getMessage(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SandboxResult(-1, "", "Interrupted: " + e.getMessage(), false);
        }
    }
}
