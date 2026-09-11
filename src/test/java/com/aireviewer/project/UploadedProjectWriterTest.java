package com.aireviewer.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UploadedProjectWriterTest {

    @TempDir Path base;

    private static UploadedProjectFile file(String relativePath, String content) {
        return new UploadedProjectFile(relativePath,
                () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private UploadedProjectWriter writer() {
        return new UploadedProjectWriter(base, 100, 1_000);
    }

    private long entriesUnderBase() throws IOException {
        try (Stream<Path> entries = Files.list(base)) {
            return entries.count();
        }
    }

    @Test
    void writesNestedFilesAndReturnsTheUploadedFolder() throws Exception {
        Path root = writer().write(List.of(
                file("demo/src/Main.java", "class Main {}"),
                file("demo/README.md", "# Demo")));

        assertEquals("demo", root.getFileName().toString());
        assertEquals("class Main {}", Files.readString(root.resolve("src/Main.java")));
        assertEquals("# Demo", Files.readString(root.resolve("README.md")));
    }

    @Test
    void returnsTheUploadDirectoryWhenThereIsNoSingleTopFolder() throws Exception {
        Path root = writer().write(List.of(file("a/A.java", "a"), file("b/B.java", "b")));

        assertTrue(Files.isRegularFile(root.resolve("a/A.java")));
        assertTrue(Files.isRegularFile(root.resolve("b/B.java")));
    }

    @Test
    void acceptsWindowsSeparators() throws Exception {
        Path root = writer().write(List.of(file("demo\\src\\Main.java", "x")));

        assertTrue(Files.isRegularFile(root.resolve("src/Main.java")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../escape.txt", "demo/../../escape.txt", "/etc/passwd", "C:\\Windows\\x.txt",
            "demo//Main.java", "./Main.java", "demo/", " "})
    void rejectsUnsafePathsAndWritesNothing(String unsafePath) throws Exception {
        UploadedProjectWriter writer = writer();

        assertThrows(ProjectImportException.class,
                () -> writer.write(List.of(file("demo/ok.txt", "fine"), file(unsafePath, "evil"))));

        assertEquals(0, entriesUnderBase(), "a rejected upload must not create anything");
        assertFalse(Files.exists(base.getParent().resolve("escape.txt")));
    }

    @Test
    void rejectsAnEmptyUpload() {
        assertThrows(ProjectImportException.class, () -> writer().write(List.of()));
    }

    @Test
    void rejectsTooManyFiles() {
        UploadedProjectWriter writer = new UploadedProjectWriter(base, 1, 1_000);

        ProjectImportException failure = assertThrows(ProjectImportException.class,
                () -> writer.write(List.of(file("demo/A.java", "a"), file("demo/B.java", "b"))));

        assertTrue(failure.getMessage().contains("limit is 1"), failure.getMessage());
    }

    @Test
    void rejectsAnUploadOverTheTotalSizeAndCleansUp() throws Exception {
        UploadedProjectWriter writer = new UploadedProjectWriter(base, 100, 10);

        assertThrows(ProjectImportException.class,
                () -> writer.write(List.of(file("demo/A.java", "123456"), file("demo/B.java", "789012"))));

        assertEquals(0, entriesUnderBase(), "the partly written upload must be removed");
    }

    @Test
    void rejectsADuplicatePathInsteadOfOverwriting() {
        ProjectImportException failure = assertThrows(ProjectImportException.class,
                () -> writer().write(List.of(file("demo/A.java", "first"), file("demo/A.java", "second"))));

        assertTrue(failure.getMessage().contains("Duplicate"), failure.getMessage());
    }

    @Test
    void closesEveryContentStream() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        UploadedProjectFile tracked = new UploadedProjectFile("demo/A.java",
                () -> new ByteArrayInputStream(new byte[] {1}) {
                    @Override
                    public void close() {
                        closed.set(true);
                    }
                });

        writer().write(List.of(tracked));

        assertTrue(closed.get());
    }

    @Test
    void closeDeletesEveryStoredUpload() throws Exception {
        UploadedProjectWriter writer = writer();
        writer.write(List.of(file("one/A.java", "a")));
        writer.write(List.of(file("two/B.java", "b")));
        assertEquals(2, entriesUnderBase());

        writer.close();

        assertEquals(0, entriesUnderBase());
    }

    @Test
    void rejectsBadConstructionArguments() {
        assertThrows(IllegalArgumentException.class, () -> new UploadedProjectWriter(base, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new UploadedProjectWriter(base, 1, 0));
        assertThrows(NullPointerException.class, () -> new UploadedProjectWriter(null, 1, 1));
    }
}
