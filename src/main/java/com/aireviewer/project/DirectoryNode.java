package com.aireviewer.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Composite pattern composite (the "branch" role, paired with the {@link FileLeaf} leaf).
 * Represents a directory: holds a growing list of child {@link FileNode}s — files and/or nested
 * directories — and answers every {@link FileNode} question, including {@link #sizeInBytes()}, by
 * delegating to those children. A caller walking the tree never special-cases "this node has
 * children" versus "this node doesn't" — it just calls {@link #children()} at any depth.
 * <p>
 * Mutable by design, unlike the immutable {@link FileLeaf}: the code that walks the filesystem
 * discovers a directory's children one at a time and appends them as it goes, so this node has to
 * support growth after construction. {@link #children()} still returns an unmodifiable view, so
 * nothing outside this package can mutate the tree once handed out.
 */
final class DirectoryNode implements FileNode {

    private final String name;
    private final Path path;
    private final List<FileNode> children = new ArrayList<>();

    DirectoryNode(String name, Path path) {
        this.name = Objects.requireNonNull(name, "name");
        this.path = Objects.requireNonNull(path, "path");
    }

    /** Appends a child node. Package-private: only this package's tree builder calls it. */
    void addChild(FileNode child) {
        Objects.requireNonNull(child, "child");
        children.add(child);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public long sizeInBytes() {
        long total = 0L;
        for (FileNode child : children) {
            total += child.sizeInBytes();
        }
        return total;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public List<FileNode> children() {
        return Collections.unmodifiableList(children);
    }
}
