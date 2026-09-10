package com.aireviewer.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Imports a directory from disk into a {@link DirectoryNode} tree, applying an
 * {@link InclusionPolicy} built internally from the three concrete {@link InclusionRule}s in this
 * package. Public entry point of the {@code project} package: callers configure it with plain
 * values (allowed extensions, a size cap, ignored directory names) and never touch
 * {@link InclusionRule}, {@link InclusionPolicy}, {@link FileLeaf} or {@link DirectoryNode}
 * directly — adding a new rule later only means changing this class's constructor, per this
 * repo's extension recipe for file-selection rules.
 * <p>
 * Two policies worth stating explicitly, since they're not obvious from the method signature
 * alone:
 * <ul>
 *   <li><b>Symbolic links are never followed</b> while walking, whether they point to a file or a
 *   directory — this rules out both a cycle (a link back to an ancestor) and a link that escapes
 *   the imported directory entirely and pulls in unrelated host files. The one exception is the
 *   root path itself: if the caller's own argument is a symlink, it is still imported, since that
 *   is an explicit instruction rather than something discovered inside untrusted project content.
 *   <li><b>One unreadable entry never aborts the import.</b> Only the root path failing validation
 *   throws {@link ProjectImportException}. A directory encountered mid-walk that can't be listed
 *   (permissions, a race with something deleting it) is logged at WARN and omitted from its
 *   parent's children — the rest of the tree still comes back.
 * </ul>
 */
public final class ProjectImporter {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectImporter.class);

    private final InclusionPolicy policy;

    public ProjectImporter(Set<String> allowedExtensions, long maxFileSizeInBytes, Set<String> ignoredDirectoryNames) {
        this.policy = new InclusionPolicy(List.of(
                new ExtensionInclusionRule(allowedExtensions),
                new SizeCapInclusionRule(maxFileSizeInBytes),
                new IgnoredDirectoryRule(ignoredDirectoryNames)
        ));
    }

    /**
     * Imports the project rooted at {@code rootPath}.
     *
     * @throws ProjectImportException if {@code rootPath} does not exist, is not a directory, or is
     *                                 not readable
     */
    public DirectoryNode importProject(Path rootPath) throws ProjectImportException {
        Path resolvedRoot = rootPath.toAbsolutePath().normalize();
        validateRoot(resolvedRoot);

        DirectoryNode root = new DirectoryNode(nameOf(resolvedRoot), resolvedRoot);
        tryOpenAndWalk(resolvedRoot, root);
        return root;
    }

    private static void validateRoot(Path resolvedRoot) throws ProjectImportException {
        if (!Files.exists(resolvedRoot)) {
            throw new ProjectImportException("Path does not exist: " + resolvedRoot);
        }
        if (!Files.isDirectory(resolvedRoot)) {
            throw new ProjectImportException("Path is not a directory: " + resolvedRoot);
        }
        if (!Files.isReadable(resolvedRoot)) {
            throw new ProjectImportException("Path is not readable: " + resolvedRoot);
        }
    }

    /**
     * Opens {@code directoryPath} for listing and visits every entry into {@code node}. Returns
     * {@code false} (and logs a warning) if the directory can't be opened at all, so the caller
     * can leave {@code node} out of its own parent rather than attaching an empty placeholder.
     */
    private boolean tryOpenAndWalk(Path directoryPath, DirectoryNode node) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
            for (Path entry : stream) {
                visitEntry(entry, node);
            }
            return true;
        } catch (IOException e) {
            LOG.warn("Skipping unreadable directory: {} ({})", directoryPath, e.getMessage());
            return false;
        }
    }

    private void visitEntry(Path entry, DirectoryNode parentNode) {
        try {
            if (Files.isSymbolicLink(entry)) {
                LOG.warn("Skipping symbolic link: {}", entry);
                return;
            }
            if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                if (!policy.isDirectoryIncluded(entry)) {
                    return;
                }
                DirectoryNode childDir = new DirectoryNode(nameOf(entry), entry);
                if (tryOpenAndWalk(entry, childDir)) {
                    parentNode.addChild(childDir);
                }
            } else {
                long sizeInBytes = Files.size(entry);
                if (policy.isFileIncluded(entry, sizeInBytes)) {
                    parentNode.addChild(new FileLeaf(nameOf(entry), entry, sizeInBytes));
                }
            }
        } catch (IOException e) {
            LOG.warn("Skipping unreadable entry: {} ({})", entry, e.getMessage());
        }
    }

    private static String nameOf(Path path) {
        Path fileName = path.getFileName();
        return fileName != null ? fileName.toString() : path.toString();
    }
}
