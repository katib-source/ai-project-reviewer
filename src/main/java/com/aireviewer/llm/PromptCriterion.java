package com.aireviewer.llm;

import java.util.Objects;

/**
 * What this package needs to know about a criterion in order to write a prompt about it: an id, a
 * human-readable name, a description of what is being judged, and the scale to judge it on.
 *
 * <p>Deliberately <em>not</em> the analysis package's own criterion type. Dependencies flow
 * {@code analysis → llm} (CLAUDE.md §0), so this package cannot import that type; and it should
 * not want to, because a criterion over there will grow fields that have nothing to do with
 * prompting — a weight, an analyzer registration, a report section. This record is the small
 * prompting-shaped view of a criterion, and the analysis-side bridge maps its own criterion onto it
 * in one line. That mapping is the seam that lets either side add fields without disturbing the
 * other.
 *
 * @param id          stable identifier, echoed into the prompt and expected back in the answer so
 *                    a reply can be matched to the question
 * @param displayName short human-readable name, e.g. "Coupling between packages"
 * @param description what the model is being asked to judge, in prose
 * @param maxScore    top of the scale, e.g. 20; the prompt asks for a score in {@code [0, maxScore]}
 */
public record PromptCriterion(String id, String displayName, String description, double maxScore) {

    /** Rejects unusable criteria at construction: a blank id or a non-positive scale is a bug, not input. */
    public PromptCriterion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        if (id.isBlank()) {
            throw new IllegalArgumentException("criterion id must not be blank");
        }
        if (!Double.isFinite(maxScore) || maxScore <= 0) {
            throw new IllegalArgumentException("maxScore must be a positive, finite number but was " + maxScore);
        }
    }
}
