package com.aireviewer.project;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Strategy pattern concrete strategy. Includes a file only if its extension (case-insensitive,
 * without the leading dot) is in the configured allow-list — e.g. {@code Set.of("java", "md")}
 * keeps source and documentation files in a run and skips everything else (build artifacts,
 * images, binaries) without the importer needing to know what a "relevant" file looks like. A
 * file with no extension is included only if the allow-list explicitly contains {@code ""}.
 * Directories are unaffected: this rule only answers {@link #isFileIncluded}.
 */
final class ExtensionInclusionRule implements InclusionRule {

    private final Set<String> allowedExtensions;

    ExtensionInclusionRule(Set<String> allowedExtensions) {
        Objects.requireNonNull(allowedExtensions, "allowedExtensions");
        Set<String> normalized = new HashSet<>();
        for (String extension : allowedExtensions) {
            Objects.requireNonNull(extension, "extension");
            normalized.add(extension.toLowerCase(Locale.ROOT));
        }
        this.allowedExtensions = Set.copyOf(normalized);
    }

    @Override
    public boolean isFileIncluded(Path path, long sizeInBytes) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String extension = dotIndex >= 0 ? fileName.substring(dotIndex + 1) : "";
        return allowedExtensions.contains(extension.toLowerCase(Locale.ROOT));
    }
}
