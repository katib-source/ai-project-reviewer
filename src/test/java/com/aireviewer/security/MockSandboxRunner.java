package com.aireviewer.security;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Mock du SandboxRunner pour les tests sans Docker.
 *
 * Permet aux autres packages (analysis, application) de tester leur code
 * sans réellement lancer Docker.
 */
public class MockSandboxRunner implements SandboxRunner {

    private SandboxResult nextResult = new SandboxResult(0, "ok", "", false);
    private int callCount = 0;
    private Path lastProjectDir;
    private List<String> lastCommand;
    private Duration lastTimeout;

    public void setNextResult(SandboxResult result) {
        this.nextResult = result;
    }

    public int getCallCount() {
        return callCount;
    }

    public Path getLastProjectDir() {
        return lastProjectDir;
    }

    public List<String> getLastCommand() {
        return lastCommand;
    }

    public Duration getLastTimeout() {
        return lastTimeout;
    }

    @Override
    public SandboxResult run(Path projectDir, List<String> command, Duration timeout) {
        this.callCount++;
        this.lastProjectDir = projectDir;
        this.lastCommand = command;
        this.lastTimeout = timeout;
        return nextResult;
    }
}
