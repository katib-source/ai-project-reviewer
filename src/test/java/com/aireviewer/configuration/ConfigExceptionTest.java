package com.aireviewer.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConfigExceptionTest {

    @Test
    void carriesTheMessage() {
        ConfigException exception = new ConfigException("bad value");

        assertEquals("bad value", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void carriesTheMessageAndCause() {
        NumberFormatException cause = new NumberFormatException("not a number");

        ConfigException exception = new ConfigException("bad value", cause);

        assertEquals("bad value", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
