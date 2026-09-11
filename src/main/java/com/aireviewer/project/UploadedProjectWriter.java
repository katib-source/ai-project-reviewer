package com.aireviewer.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Stores a project uploaded from the browser in a private temporary directory, so that
 * {@link ProjectImporter} and the analyzers read it exactly like a local path. Browsers never
 * reveal the real path of a folder the user picks, so an upload is the only way a folder picker
 * can work; everything downstream of this class stays path-based and unchanged.
 * <p>
 * Every relative path in an upload is untrusted input. A path is rejected outright — never
 * "cleaned up" — if it is absolute, carries a drive letter, or has an empty, {@code .} or
 * {@code ..} segment, so no upload can write outside its own directory. The number of files and
 * the total size are capped, a duplicate path is an error rather than a silent overwrite, and a
 * failed upload leaves nothing behind on disk.
 * <p>
 * Uploaded copies must outlive the import request, since analyzers read file content later, so
 * they are kept until {@link #close()} — called when the application shuts down.
 */
public final class UploadedProjectWriter implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(UploadedProjectWriter.class);

    private final Path baseDirectory;
    private final int maxFiles;
    private final long maxTotalBytes;
    private final List<Path> createdDirectories = new CopyOnWriteArrayList<>();

    /**
     * @param baseDirectory where each upload gets its own fresh sub-directory
     * @param maxFiles      most files accepted in one upload
     * @param maxTotalBytes most bytes accepted in one upload, all files together
     */
    public UploadedProjectWriter(Path baseDirectory, int maxFiles, long maxTotalBytes) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory");
        if (maxFiles < 1) {
            throw new IllegalArgumentException("maxFiles must be >= 1, got: " + maxFiles);
        }
        if (maxTotalBytes < 1) {
            throw new IllegalArgumentException("maxTotalBytes must be >= 1, got: " + maxTotalBytes);
        }
        this.maxFiles = maxFiles;
        this.maxTotalBytes = maxTotalBytes;
    }

    /**
     * Writes every file of the upload and returns the directory to import: the uploaded folder
     * itself when all paths share one top-level folder (the browser's folder picker always
     * produces that shape), otherwise the upload's own directory.
     *
     * @throws ProjectImportException if the upload is empty, too large, has an unsafe or duplicate
     *                                path, or cannot be written; nothing is left on disk
     */
    public Path write(List<UploadedProjectFile> files) throws ProjectImportException {
        Objects.requireNonNull(files, "files");
        if (files.isEmpty()) {
            throw new ProjectImportException("The upload contained no files");
        }
        if (files.size() > maxFiles) {
            throw new ProjectImportException(
                    "The upload has " + files.size() + " files; the limit is " + maxFiles);
        }
        List<List<String>> segmentsPerFile = new ArrayList<>(files.size());
        for (UploadedProjectFile file : files) {
            segmentsPerFile.add(safeSegments(file.relativePath()));
        }

        Path uploadDirectory = createUploadDirectory();
        try {
            long totalBytes = 0;
            for (int i = 0; i < files.size(); i++) {
                Path target = resolveInside(uploadDirectory, segmentsPerFile.get(i));
                totalBytes += copy(files.get(i), target, maxTotalBytes - totalBytes);
            }
            LOG.info("Stored uploaded project: {} files, {} bytes", files.size(), totalBytes);
            return importRoot(uploadDirectory, segmentsPerFile);
        } catch (ProjectImportException | RuntimeException e) {
            deleteRecursively(uploadDirectory);
            createdDirectories.remove(uploadDirectory);
            throw e;
        }
    }

    /** Deletes every upload this writer stored. Failures are logged, never thrown. */
    @Override
    public void close() {
        for (Path directory : createdDirectories) {
            deleteRecursively(directory);
        }
        createdDirectories.clear();
    }

    /**
     * Splits an untrusted relative path into segments, rejecting anything that could escape the
     * upload directory. Backslashes count as separators so a Windows-style path is judged the
     * same way as a POSIX one.
     */
    static List<String> safeSegments(String relativePath) throws ProjectImportException {
        if (relativePath.isBlank() || relativePath.indexOf('\0') >= 0) {
            throw new ProjectImportException("Invalid file path in upload: '" + relativePath + "'");
        }
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new ProjectImportException("Absolute file path in upload: '" + relativePath + "'");
        }
        List<String> segments = List.of(normalized.split("/", -1));
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new ProjectImportException("Unsafe file path in upload: '" + relativePath + "'");
            }
        }
        return segments;
    }

    private Path createUploadDirectory() throws ProjectImportException {
        try {
            Files.createDirectories(baseDirectory);
            Path directory = Files.createTempDirectory(baseDirectory, "upload-");
            createdDirectories.add(directory);
            return directory;
        } catch (IOException e) {
            throw new ProjectImportException("Could not create a directory for the upload", e);
        }
    }

    private static Path resolveInside(Path uploadDirectory, List<String> segments) throws ProjectImportException {
        Path target = uploadDirectory;
        for (String segment : segments) {
            target = target.resolve(segment);
        }
        // Defence in depth: safeSegments already guarantees this.
        if (!target.normalize().startsWith(uploadDirectory)) {
            throw new ProjectImportException("Unsafe file path in upload: '" + String.join("/", segments) + "'");
        }
        return target;
    }

    /** Copies one file, failing as soon as the running total would pass the upload limit. */
    private long copy(UploadedProjectFile file, Path target, long bytesLeft) throws ProjectImportException {
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.content().get();
                 OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[8192];
                long copied = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    copied += read;
                    if (copied > bytesLeft) {
                        throw new ProjectImportException(
                                "The upload is larger than the " + describeSize(maxTotalBytes) + " limit");
                    }
                    out.write(buffer, 0, read);
                }
                return copied;
            }
        } catch (FileAlreadyExistsException e) {
            throw new ProjectImportException("Duplicate file path in upload: '" + file.relativePath() + "'", e);
        } catch (IOException e) {
            throw new ProjectImportException("Could not store uploaded file '" + file.relativePath() + "'", e);
        }
    }

    private static String describeSize(long bytes) {
        return bytes >= 1_000_000 ? bytes / 1_000_000 + " MB" : bytes + " bytes";
    }

    private static Path importRoot(Path uploadDirectory, List<List<String>> segmentsPerFile) {
        String topFolder = segmentsPerFile.getFirst().getFirst();
        boolean singleTopFolder = segmentsPerFile.stream()
                .allMatch(segments -> segments.size() > 1 && segments.getFirst().equals(topFolder));
        return singleTopFolder ? uploadDirectory.resolve(topFolder) : uploadDirectory;
    }

    private static void deleteRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            LOG.warn("Could not fully delete upload directory {} ({})", directory, e.getMessage());
        }
    }
}
