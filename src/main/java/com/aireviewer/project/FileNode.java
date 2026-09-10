package com.aireviewer.project;

import java.nio.file.Path;
import java.util.List;

/**
 * Composite pattern component. Problem: a project directory is a tree of unknown depth mixing
 * files and directories, and every consumer (analyzers, the report, the web tree view) needs to
 * walk it without caring which kind of node it currently holds. A single shared interface lets a
 * directory and a file answer the same questions — name, path, size, children — so callers write
 * one recursive algorithm instead of branching on type at every call site.
 */
public interface FileNode {

    /** File or directory name (last path segment), never blank. */
    String name();

    /** Absolute or project-relative path of this node. */
    Path path();

    /** Size in bytes: the file's own size for a leaf, the recursive sum of children for a directory. */
    long sizeInBytes();

    /** {@code true} for a directory node, {@code false} for a file leaf. */
    boolean isDirectory();

    /** Child nodes, in a defined order. Always empty (never {@code null}) for a file leaf. */
    List<FileNode> children();
}
