package com.aireviewer.analysis;

import java.util.Objects;

/**
 * One evaluation criterion an {@link Analyzer} can be run against — e.g. "naming conventions" or
 * "file size statistics". {@code id} is the stable key every other package references it by: a
 * {@link CriterionResult} carries the {@link Criterion} it came from, {@code persistence} stores
 * results keyed by criterion id, and {@code web} lists criteria by id for the frontend's checklist
 * ({@code GET /api/criteria} in the API contract).
 *
 * @param id          stable, unique identifier, e.g. {@code "naming-conventions"}
 * @param name        short display name shown in the criteria checklist
 * @param description longer explanation of what this criterion checks, also shown to the user
 * @param weight      this criterion's share of the consolidated score; must be strictly positive —
 *                    a zero or negative weight could never meaningfully contribute to a weighted
 *                    average
 */
public record Criterion(String id, String name, String description, double weight) {

    public Criterion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        // NaN, zero and negative weights are all rejected by this one check: any comparison
        // against NaN evaluates to false in Java, so "weight > 0" is already false for NaN too.
        if (!(weight > 0)) {
            throw new IllegalArgumentException("weight must be > 0, got: " + weight);
        }
    }
}
