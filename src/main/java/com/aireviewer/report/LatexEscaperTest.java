package com.aireviewer.report;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class LatexEscaperTest {
    @Test void escapesAllCommonLatexSpecialCharacters() {
        assertEquals("\\# \\$ \\% \\& \\_ \\{ \\} \\textasciicircum{} \\textasciitilde{} \\textbackslash{}",
                new LatexEscaper().escape("# $ % & _ { } ^ ~ \\"));
    }
    @Test void leavesOrdinaryTextUntouched() { assertEquals("Architecture score: 8/10", new LatexEscaper().escape("Architecture score: 8/10")); }
    @Test void escapesProjectPathsAndLlmText() { assertEquals("C:\\textbackslash{}\_work\\textbackslash{}review 50\\%", new LatexEscaper().escape("C:\\_work\\review 50%")); }
}
