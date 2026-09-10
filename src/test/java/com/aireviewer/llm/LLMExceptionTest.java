package com.aireviewer.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LLMExceptionTest {

    @Test
    @DisplayName("carries its kind and message")
    void carriesKindAndMessage() {
        LLMException exception = new LLMException(LLMException.Kind.TIMEOUT, "no answer after 30s");

        assertEquals(LLMException.Kind.TIMEOUT, exception.kind());
        assertEquals("no answer after 30s", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("preserves the underlying cause")
    void preservesCause() {
        IOException cause = new IOException("connection refused");

        LLMException exception =
                new LLMException(LLMException.Kind.UNAVAILABLE, "provider unreachable", cause);

        assertEquals(LLMException.Kind.UNAVAILABLE, exception.kind());
        assertSame(cause, exception.getCause());
    }

    @Test
    @DisplayName("covers every failure mode the resilience layer must distinguish")
    void kindCoversKnownFailureModes() {
        assertEquals(6, LLMException.Kind.values().length);
        for (LLMException.Kind kind : LLMException.Kind.values()) {
            assertEquals(kind, new LLMException(kind, "boom").kind());
        }
    }
}
