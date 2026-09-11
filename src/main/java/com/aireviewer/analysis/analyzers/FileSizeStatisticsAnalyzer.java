package com.aireviewer.analysis.analyzers;

import com.aireviewer.analysis.AbstractAnalyzer;
import com.aireviewer.analysis.Criterion;
import com.aireviewer.analysis.CriterionResult;
import com.aireviewer.project.FileNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic criterion: flags a project dominated by a handful of oversized files, relative to
 * the files actually handed to analysis. {@code project}'s own {@code SizeCapInclusionRule} already
 * keeps any single pathologically huge file out of the imported tree entirely, so this isn't a
 * redundant re-check of that same absolute cap — it's a relative health check of what's left: what
 * fraction of the included files are still large enough, on their own terms, to be worth flagging.
 */
public final class FileSizeStatisticsAnalyzer extends AbstractAnalyzer {

    private static final Criterion CRITERION = new Criterion(
            "file-size-statistics",
            "File Size Statistics",
            "Checks whether the project's included files are reasonably sized, flagging a "
                    + "codebase dominated by a few oversized files.",
            1.0
    );

    private final long largeFileThresholdBytes;

    /**
     * @param largeFileThresholdBytes a file strictly larger than this many bytes counts as
     *                                "large" for scoring purposes
     */
    public FileSizeStatisticsAnalyzer(long largeFileThresholdBytes) {
        if (largeFileThresholdBytes < 0) {
            throw new IllegalArgumentException(
                    "largeFileThresholdBytes must be >= 0, got: " + largeFileThresholdBytes);
        }
        this.largeFileThresholdBytes = largeFileThresholdBytes;
    }

    @Override
    public Criterion criterion() {
        return CRITERION;
    }

    @Override
    protected CriterionResult doAnalyze(FileNode projectRoot) {
        List<FileNode> files = new ArrayList<>();
        collectFiles(projectRoot, files);

        if (files.isEmpty()) {
            return CriterionResult.failed(CRITERION, "No files were available to analyze.");
        }

        long totalBytes = 0L;
        long largeFileCount = 0L;
        for (FileNode file : files) {
            long size = file.sizeInBytes();
            totalBytes += size;
            if (size > largeFileThresholdBytes) {
                largeFileCount++;
            }
        }

        int fileCount = files.size();
        long averageBytes = totalBytes / fileCount;
        double largeFileFraction = largeFileCount / (double) fileCount;
        double score = 10.0 * (1.0 - largeFileFraction);

        String comment = String.format(
                "%d file(s), %d byte(s) total, %d byte(s) average; %d file(s) (%.0f%%) exceed the "
                        + "%d-byte large-file threshold.",
                fileCount, totalBytes, averageBytes, largeFileCount, largeFileFraction * 100, largeFileThresholdBytes
        );

        return CriterionResult.success(CRITERION, score, comment);
    }

    private static void collectFiles(FileNode node, List<FileNode> files) {
        if (node.isDirectory()) {
            for (FileNode child : node.children()) {
                collectFiles(child, files);
            }
        } else {
            files.add(node);
        }
    }
}
