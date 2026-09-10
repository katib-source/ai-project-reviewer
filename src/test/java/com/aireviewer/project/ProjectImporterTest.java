package com.aireviewer.project;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectImporterTest {

    private static final Set<String> JAVA_EXTENSIONS = Set.of("java");
    private static final long GENEROUS_SIZE_CAP = 1_000_000L;
    private static final Set<String> DEFAULT_IGNORES = Set.of("target", "node_modules", ".git");

    private static ProjectImporter defaultImporter() {
        return new ProjectImporter(JAVA_EXTENSIONS, GENEROUS_SIZE_CAP, DEFAULT_IGNORES);
    }

    private static List<String> childNames(DirectoryNode node) {
        return node.children().stream().map(FileNode::name).toList();
    }

    @Test
    void importsIncludedFilesFromAFlatDirectory(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("Main.java"), "class Main {}");
        Files.writeString(tempDir.resolve("readme.md"), "hello");

        DirectoryNode root = defaultImporter().importProject(tempDir);

        assertEquals(List.of("Main.java"), childNames(root));
    }

    @Test
    void excludesFilesOverTheSizeCap(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("Small.java"), "ok");
        Files.writeString(tempDir.resolve("Big.java"), "x".repeat(2000));

        ProjectImporter importer = new ProjectImporter(JAVA_EXTENSIONS, 100L, DEFAULT_IGNORES);
        DirectoryNode root = importer.importProject(tempDir);

        assertEquals(List.of("Small.java"), childNames(root));
    }

    @Test
    void excludesIgnoredDirectoriesEntirely(@TempDir Path tempDir) throws Exception {
        Path nodeModules = Files.createDirectory(tempDir.resolve("node_modules"));
        Files.writeString(nodeModules.resolve("lib.java"), "irrelevant");
        Files.writeString(tempDir.resolve("Main.java"), "class Main {}");

        DirectoryNode root = defaultImporter().importProject(tempDir);

        assertEquals(List.of("Main.java"), childNames(root));
    }

    @Test
    void recursesIntoNestedIncludedDirectories(@TempDir Path tempDir) throws Exception {
        Path nested = Files.createDirectory(tempDir.resolve("nested"));
        Files.writeString(nested.resolve("Inner.java"), "class Inner {}");

        DirectoryNode root = defaultImporter().importProject(tempDir);

        assertEquals(1, root.children().size());
        DirectoryNode nestedNode = (DirectoryNode) root.children().get(0);
        assertEquals("nested", nestedNode.name());
        assertEquals(List.of("Inner.java"), childNames(nestedNode));
    }

    @Test
    void skipsSymbolicLinkEntriesFoundDuringTheWalk(@TempDir Path tempDir) throws Exception {
        Path realFile = Files.writeString(tempDir.resolve("Main.java"), "class Main {}");
        try {
            Files.createSymbolicLink(tempDir.resolve("Link.java"), realFile);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("Symbolic links are not supported on this filesystem");
        }

        DirectoryNode root = defaultImporter().importProject(tempDir);

        assertEquals(List.of("Main.java"), childNames(root));
    }

    @Test
    void followsTheRootPathEvenWhenTheRootItselfIsASymlink(@TempDir Path tempDir) throws Exception {
        Path realProject = Files.createDirectory(tempDir.resolve("real-project"));
        Files.writeString(realProject.resolve("Main.java"), "class Main {}");
        Path link = tempDir.resolve("link-to-project");
        try {
            Files.createSymbolicLink(link, realProject);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("Symbolic links are not supported on this filesystem");
        }

        DirectoryNode root = defaultImporter().importProject(link);

        assertEquals(List.of("Main.java"), childNames(root));
    }

    @Test
    void skipsAnUnreadableDirectoryWithoutAbortingTheRestOfTheImport(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX permissions are not supported on this filesystem");

        Path blocked = Files.createDirectory(tempDir.resolve("blocked"));
        Files.writeString(blocked.resolve("Secret.java"), "class Secret {}");
        Files.writeString(tempDir.resolve("Main.java"), "class Main {}");

        Files.setPosixFilePermissions(blocked, PosixFilePermissions.fromString("---------"));
        try {
            DirectoryNode root = defaultImporter().importProject(tempDir);

            assertEquals(List.of("Main.java"), childNames(root));
        } finally {
            Files.setPosixFilePermissions(blocked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void rejectsAMissingRootPath(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");

        assertThrows(ProjectImportException.class, () -> defaultImporter().importProject(missing));
    }

    @Test
    void rejectsARootPathThatIsAFileNotADirectory(@TempDir Path tempDir) throws Exception {
        Path file = Files.writeString(tempDir.resolve("Main.java"), "class Main {}");

        assertThrows(ProjectImportException.class, () -> defaultImporter().importProject(file));
    }

    @Test
    void emptyDirectoryImportsAsAnEmptyTree(@TempDir Path tempDir) throws Exception {
        DirectoryNode root = defaultImporter().importProject(tempDir);

        assertTrue(root.children().isEmpty());
        assertTrue(root.isDirectory());
    }
}
