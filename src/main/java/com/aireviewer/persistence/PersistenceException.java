package com.aireviewer.persistence;

/**
 * Thrown when the history file cannot be read, parsed or written. Checked, per this repo's
 * convention for I/O boundaries — a caller must decide how to react (show the user an error,
 * retry, treat history as unavailable for this run), it can't be ignored.
 */
public final class PersistenceException extends Exception {

    private static final long serialVersionUID = 1L;

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
