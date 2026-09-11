package com.aireviewer.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LatexReportBuilderTest {
    @Test void buildsCompleteDocumentAndEscapesData() {
        String tex = new LatexReportBuilder(new LatexEscaper()).project("demo_project", "/tmp/demo_project").overallScore(8.25)
                .addCriterion("code_quality", "Code & quality", 8.25, "Avoid 100% duplicated code").build();
        assertTrue(tex.startsWith("\\documentclass")); assertTrue(tex.contains("\\begin{document}")); assertTrue(tex.contains("\\section*{Project}"));
        assertTrue(tex.contains("demo\\_project")); assertTrue(tex.contains("Code \\& quality")); assertTrue(tex.contains("100\\% duplicated code")); assertTrue(tex.endsWith("\\end{document}\n"));
    }
}