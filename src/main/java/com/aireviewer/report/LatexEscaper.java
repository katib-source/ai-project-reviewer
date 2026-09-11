package com.aireviewer.report;

import java.util.Objects;

/** Escapes untrusted application text before it is inserted into ordinary LaTeX text mode. */
public final class LatexEscaper {
    public String escape(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case '\\' -> out.append("\\textbackslash{}");
                case '{' -> out.append("\\{");
                case '}' -> out.append("\\}");
                case '#' -> out.append("\\#");
                case '$' -> out.append("\\$");
                case '%' -> out.append("\\%");
                case '&' -> out.append("\\&");
                case '_' -> out.append("\\_");
                case '^' -> out.append("\\textasciicircum{}");
                case '~' -> out.append("\\textasciitilde{}");
                default -> out.append(value.charAt(i));
            }
        }
        return out.toString();
    }
}
