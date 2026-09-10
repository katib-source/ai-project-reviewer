package com.aireviewer.llm;

/**
 * Checked exception for every way an LLM call can fail, carrying a {@link Kind} that says which
 * way it was.
 *
 * <p>One exception type with a kind, rather than six exception classes: callers do not branch on
 * failure type the way a class hierarchy would reward. The analysis engine's reaction to any
 * failure is the same — record a failed result for the criterion and carry on with the rest of the
 * run — and the resilience decorator only needs to know whether a failure is worth retrying,
 * which is a property of the kind (a {@link Kind#TIMEOUT} or {@link Kind#UNAVAILABLE} is; a
 * {@link Kind#SCHEMA_VALIDATION} is not). Six {@code catch} blocks that all do the same thing, or
 * a hierarchy nobody dispatches on, would be ceremony without a payoff, and every new failure mode
 * would add a class to the public API of this package. A new kind is one enum constant.
 *
 * <p>It is checked on purpose: reaching an external model over the network is expected to fail
 * routinely, so the compiler should force every caller to decide what happens then.
 */
public class LLMException extends Exception {

    /** Serialization id; exceptions cross no wire here, but the compiler asks for it. */
    private static final long serialVersionUID = 1L;

    /** What went wrong. Kept coarse — each constant maps to a distinct reaction, not to an API error code. */
    public enum Kind {
        /** The provider did not answer within the allotted time. Retryable. */
        TIMEOUT(true),
        /**
         * The provider answered with an error status (auth, quota, bad request, server error).
         *
         * <p>Not retryable: at this granularity the kind cannot tell a 401 from a 503, and retrying
         * a rejected key just burns the quota. An adapter that can distinguish them should map the
         * transient statuses (429, 5xx) to {@link #UNAVAILABLE} instead.
         */
        HTTP_ERROR(false),
        /** The provider could not be reached at all: DNS, connection refused, service down. Retryable. */
        UNAVAILABLE(true),
        /** A well-formed exchange that carried no usable text — no choices, or blank content. */
        EMPTY_RESPONSE(false),
        /** Text came back, but it is not the shape it claimed to be — e.g. unparseable JSON. */
        MALFORMED_RESPONSE(false),
        /** Parseable content that violates the contract: missing required fields, score out of range. */
        SCHEMA_VALIDATION(false);

        private final boolean retryable;

        Kind(boolean retryable) {
            this.retryable = retryable;
        }

        /**
         * Whether sending the very same request again could plausibly succeed.
         *
         * <p>The knowledge lives here, on the failure kind, rather than in whatever code happens to
         * be retrying: a timeout is worth another go no matter who catches it, and a model that
         * answered with the wrong schema will answer with the wrong schema again. Keeping the
         * decision in one place means the resilience layer cannot disagree with the documentation
         * on these constants, and a new kind declares its own retry policy as it is added.
         *
         * @return {@code true} if a retry is worth attempting
         */
        public boolean isRetryable() {
            return retryable;
        }
    }

    private final Kind kind;

    /**
     * @param kind    what went wrong, never {@code null}
     * @param message context for a human reader — must not contain an API key, a full request body,
     *                or the contents of an analyzed file
     */
    public LLMException(Kind kind, String message) {
        super(message);
        this.kind = requireKind(kind);
    }

    /**
     * @param kind    what went wrong, never {@code null}
     * @param message context for a human reader — see {@link #LLMException(Kind, String)}
     * @param cause   the underlying failure, kept so the stack trace stays diagnosable
     */
    public LLMException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = requireKind(kind);
    }

    /**
     * The failure category, for callers deciding whether to retry, fall back, or give up.
     *
     * @return the kind, never {@code null}
     */
    public Kind kind() {
        return kind;
    }

    private static Kind requireKind(Kind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        return kind;
    }
}
