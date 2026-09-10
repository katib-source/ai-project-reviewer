package com.aireviewer.project;

import java.nio.file.Path;

/**
 * Strategy pattern concrete strategy. Includes a file only if its size does not exceed a
 * configured cap, so an oversized generated or vendored file (a bundled JS blob, a data dump)
 * can't blow up analysis time or an LLM prompt just because it happened to have an allowed
 * extension. Directories are unaffected: this rule only answers {@link #isFileIncluded}.
 */
final class SizeCapInclusionRule implements InclusionRule {

    private final long maxSizeInBytes;

    SizeCapInclusionRule(long maxSizeInBytes) {
        if (maxSizeInBytes < 0) {
            throw new IllegalArgumentException("maxSizeInBytes must be >= 0, got: " + maxSizeInBytes);
        }
        this.maxSizeInBytes = maxSizeInBytes;
    }

    @Override
    public boolean isFileIncluded(Path path, long sizeInBytes) {
        return sizeInBytes <= maxSizeInBytes;
    }
}
