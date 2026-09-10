package com.aireviewer.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LLMResponseTest {

    @Test
    @DisplayName("holds the raw content untouched, with no parsing")
    void holdsRawContent() {
        String raw = "  {\"score\": 14, \"comment\": \"decent\"}  ";

        LLMResponse response = new LLMResponse(raw, "mistral-small-latest", false);

        assertEquals(raw, response.content(), "content must be carried verbatim, not trimmed or parsed");
        assertEquals("mistral-small-latest", response.model());
        assertFalse(response.fromFallback());
    }

    @Test
    @DisplayName("flags an answer produced by a fallback provider")
    void flagsFallback() {
        LLMResponse response = new LLMResponse("{}", "mock", true);

        assertTrue(response.fromFallback());
        assertEquals("mock", response.model());
    }
}
