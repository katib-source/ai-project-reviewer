package com.aireviewer.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PersistenceExceptionTest {

    @Test
    void carriesTheMessage() {
        PersistenceException exception = new PersistenceException("could not read history");

        assertEquals("could not read history", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void carriesTheMessageAndCause() {
        IOException cause = new IOException("disk full");

        PersistenceException exception = new PersistenceException("could not write history", cause);

        assertEquals("could not write history", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
