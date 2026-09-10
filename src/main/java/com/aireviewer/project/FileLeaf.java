package com.aireviewer.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Composite pattern leaf. Represents a single file in the project tree; always reports zero
 * children so tree-walking code never needs a special case for "nothing to recurse into" versus
 * "this node cannot have children by definition" — both are just an empty {@link #children()}.
 */
record FileLeaf(String name, Path path, long sizeInBytes) implements FileNode {

    FileLeaf {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        if (sizeInBytes < 0) {
            throw new IllegalArgumentException("sizeInBytes must be >= 0, got: " + sizeInBytes);
        }
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public List<FileNode> children() {
        return List.of();
    }
}
