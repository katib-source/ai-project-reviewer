package com.aireviewer.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Builder pattern for assembling a complete, ordered LaTeX evaluation document. */
public final class LatexReportBuilder {
    private final LatexEscaper escaper;
    private String title = "AI Project Reviewer - Evaluation";
    private String projectName = "Unknown project";
    private String projectPath = "";
    private Double overallScore;
    private final List<CriterionSection> criteria = new ArrayList<>();

    public LatexReportBuilder(LatexEscaper escaper) { this.escaper = Objects.requireNonNull(escaper, "escaper"); }
    public LatexReportBuilder title(String title) { this.title = requireNonBlank(title, "title"); return this; }
    public LatexReportBuilder project(String name, String path) {
        this.projectName = requireNonBlank(name, "name");
        this.projectPath = Objects.requireNonNull(path, "path");
        return this;
    }
    public LatexReportBuilder overallScore(double score) {
        if (!Double.isFinite(score)) throw new IllegalArgumentException("score must be finite");
        this.overallScore = score;
        return this;
    }
    public LatexReportBuilder addCriterion(String id, String name, double score, String comment) {
        if (!Double.isFinite(score)) throw new IllegalArgumentException("score must be finite");
        criteria.add(new CriterionSection(requireNonBlank(id, "id"), requireNonBlank(name, "name"),
                score, Objects.requireNonNull(comment, "comment")));
        return this;
    }
    public String build() {
        StringBuilder tex = new StringBuilder(4096);
        tex.append("\\documentclass[11pt,a4paper]{article}\n")
                .append("\\usepackage[utf8]{inputenc}\n")
                .append("\\usepackage[T1]{fontenc}\n")
                .append("\\usepackage{geometry}\n")
                .append("\\usepackage{hyperref}\n")
                .append("\\geometry{margin=2.5cm}\n")
                .append("\\begin{document}\n\n")
                .append("\\title{").append(escaper.escape(title)).append("}\n")
                .append("\\maketitle\n")
                .append("\\section*{Project}\n")
                .append("\\textbf{Name:} ").append(escaper.escape(projectName)).append("\\\\\n")
                .append("\\textbf{Path:} ").append(escaper.escape(projectPath)).append("\n\n");
        if (overallScore != null) {
            tex.append("\\section*{Overall score}\n")
                    .append(formatScore(overallScore)).append(" / 10\n\n");
        }
        tex.append("\\section*{Criteria}\n");
        if (criteria.isEmpty()) {
            tex.append("No criterion result is available.\n\n");
        } else {
            for (CriterionSection criterion : criteria) {
                tex.append("\\subsection*{").append(escaper.escape(criterion.name())).append("}\n")
                        .append("\\textbf{Criterion ID:} ").append(escaper.escape(criterion.id())).append("\\\\\n")
                        .append("\\textbf{Score:} ").append(formatScore(criterion.score())).append(" / 10\n\n")
                        .append(escaper.escape(criterion.comment())).append("\n\n");
            }
        }
        return tex.append("\\end{document}\n").toString();
    }
    private static String formatScore(double score) { return String.format(Locale.ROOT, "%.2f", score); }
    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
    private record CriterionSection(String id, String name, double score, String comment) { }
}
