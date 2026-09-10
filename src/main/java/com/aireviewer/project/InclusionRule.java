package com.aireviewer.project;

import java.nio.file.Path;

/**
 * Strategy pattern: one file-selection algorithm per implementation (by extension, by size, by
 * ignored directory name, ...), so an {@code InclusionPolicy} can combine an open-ended list of
 * them without any of its own code — or any caller's — knowing which specific rules are active.
 * Adding a new selection rule is exactly one new class implementing this interface; nothing else
 * changes.
 * <p>
 * Two independent questions, each defaulted to "yes" so a rule only overrides the one it actually
 * cares about: {@link #isFileIncluded} decides whether a file becomes a leaf in the tree;
 * {@link #isDirectoryIncluded} decides whether the importer descends into a directory at all (a
 * {@code false} here prunes the whole subtree without needing to check every file inside it).
 */
public interface InclusionRule {

    /** Whether the file at {@code path}, of size {@code sizeInBytes}, belongs in the tree. */
    default boolean isFileIncluded(Path path, long sizeInBytes) {
        return true;
    }

    /** Whether the importer should descend into the directory at {@code path} at all. */
    default boolean isDirectoryIncluded(Path path) {
        return true;
    }
}
