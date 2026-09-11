package com.aireviewer.project;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One file of a project uploaded from the browser: its path relative to the chosen folder
 * (untrusted, validated by {@link UploadedProjectWriter}) and a way to open its content.
 * The content is opened lazily so an upload of thousands of files never holds thousands of
 * streams open at once.
 */
public record UploadedProjectFile(String relativePath, Supplier<InputStream> content) {

    public UploadedProjectFile {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
    }
}
