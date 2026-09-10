package com.aireviewer.project;

/**
 * Thrown when a project cannot be imported at all: the root path is missing, not a directory, or
 * not readable. Checked, per this repo's convention for I/O boundaries — a caller must decide how
 * to react (show the user an error, retry with another path), it can't be ignored.
 */
public final class ProjectImportException extends Exception {

    private static final long serialVersionUID = 1L;

    public ProjectImportException(String message) {
        super(message);
    }

    public ProjectImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
