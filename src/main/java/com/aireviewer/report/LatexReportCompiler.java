package com.aireviewer.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Optional pdflatex compiler with no-shell-escape and a hard timeout. */
public final class LatexReportCompiler {
    private static final Logger LOG = LoggerFactory.getLogger(LatexReportCompiler.class);
    private final Duration timeout;
    public LatexReportCompiler(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
    }
    public Path compile(Path texFile) throws IOException, InterruptedException {
        Objects.requireNonNull(texFile, "texFile");
        if (!Files.isRegularFile(texFile)) throw new IOException("LaTeX source does not exist: " + texFile);
        Path dir = texFile.toAbsolutePath().getParent();
        Process process = new ProcessBuilder(List.of(
                "pdflatex", "-interaction=nonstopmode", "-halt-on-error", "-no-shell-escape",
                texFile.getFileName().toString()))
                .directory(dir.toFile()).redirectErrorStream(true).start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            LOG.warn("pdflatex timed out for {}", texFile);
            throw new IOException("pdflatex timed out after " + timeout);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new IOException("pdflatex failed with exit code " + process.exitValue() + ": " + lastLines(output));
        return dir.resolve(strip(texFile.getFileName().toString()) + ".pdf");
    }
    private static String strip(String name) { return name.endsWith(".tex") ? name.substring(0, name.length() - 4) : name; }
    private static String lastLines(String text) {
        String[] lines = text.split("\\R");
        return String.join(" | ", Arrays.copyOfRange(lines, Math.max(0, lines.length - 8), lines.length));
    }
}
