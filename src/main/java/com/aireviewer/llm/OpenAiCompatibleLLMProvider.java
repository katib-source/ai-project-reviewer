package com.aireviewer.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Adapter</b> base for every vendor that speaks the OpenAI-compatible chat-completions shape:
 * {@code POST {baseUrl}/chat/completions} with a model name and a {@code messages} array, answering
 * with a {@code choices} array.
 *
 * <p>Problem: Mistral's hosted API and a local LM Studio server are, at the wire level, the same
 * API. Writing each as an independent adapter would duplicate the HTTP call, the timeout handling,
 * the status-to-{@link LLMException.Kind} mapping and the response parsing — and worse, would let
 * the two copies drift, so that a timeout against one provider retried while the same timeout
 * against the other did not. The shared plumbing lives here once; a subclass supplies only what
 * genuinely differs: {@link #baseUrl()}, {@link #model()}, {@link #apiKey()} and
 * {@link #requestTimeout()}. Adding a third OpenAI-compatible vendor is a subclass of four short
 * methods, which is the extension recipe CLAUDE.md §7 promises.
 *
 * <h2>Two messages, not one blob — the second defense layer</h2>
 * The body is built as <b>two separate messages</b>: a {@code system} message holding only trusted
 * text (the persona, our instructions, the required output format) and a {@code user} message
 * holding only {@link LLMRequest#untrustedContent()}. This is deliberate. Providers weight the
 * system role more heavily than the user role, so the split gives the analyzed project's text
 * strictly less authority than our instructions — a defense that works at the model's own
 * priority level, independently of the delimiters and warnings {@link PromptBuilder} writes into
 * the content.
 *
 * <p>That is also the reason {@link LLMRequest} has kept {@code instructions} and
 * {@code untrustedContent} as separate fields since the first step: had they ever been concatenated
 * into one prompt string, the boundary needed here would no longer exist, and this adapter would
 * have no way to reconstruct which half deserved authority. Both layers are only as good as that
 * split, so a subclass must never merge the two fields.
 *
 * <h2>Logging and secrets</h2>
 * No API key, request body or raw response body is ever logged or put in an exception message
 * (CLAUDE.md §4). Request bodies are never quoted at all, since they carry analyzed project
 * content; response snippets are truncated, whitespace-collapsed, and passed through
 * {@link #redactSecrets(String)} so that even a server echoing our key back cannot leak it.
 *
 * <p>Thread-safe: {@link HttpClient} is, and this class holds no other mutable state.
 */
public abstract class OpenAiCompatibleLLMProvider implements LLMProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatibleLLMProvider.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** How much of a response body may appear in an exception message. */
    private static final int SNIPPET_LIMIT = 160;

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";

    /**
     * Created on first use rather than in the constructor: building it needs
     * {@link #requestTimeout()}, and calling an overridable method from a base constructor would
     * run before the subclass had initialized the fields that method reads.
     */
    private volatile HttpClient httpClient;

    /** @return API root, without a trailing slash, e.g. {@code https://api.mistral.ai/v1} */
    protected abstract String baseUrl();

    /** @return the model to ask for, e.g. {@code mistral-small-latest} */
    protected abstract String model();

    /** @return the API key, or {@link Optional#empty()} for a provider that needs none */
    protected abstract Optional<String> apiKey();

    /** @return how long to wait for a complete answer before giving up */
    protected abstract Duration requestTimeout();

    @Override
    public LLMResponse complete(LLMRequest request) throws LLMException {
        Objects.requireNonNull(request, "request");

        URI endpoint = URI.create(baseUrl() + "/chat/completions");
        HttpRequest httpRequest = buildHttpRequest(endpoint, requestBody(request));

        LOG.debug("Requesting evaluation of criterion '{}' from {} (model={}, timeout={})",
                request.criterionId(), endpoint, model(), requestTimeout());

        HttpResponse<String> response = send(httpRequest, request);
        return toLLMResponse(response, request);
    }

    private HttpRequest buildHttpRequest(URI endpoint, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        // Only when configured: a local server needs no credentials, and sending a bogus header
        // would be a needless way to get rejected.
        apiKey().ifPresent(key -> builder.header("Authorization", "Bearer " + key));

        return builder.build();
    }

    /**
     * Builds the JSON body, keeping trusted and untrusted text in different messages — see this
     * class's documentation for why the split carries security weight.
     */
    private String requestBody(LLMRequest request) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model());
        root.put("temperature", request.temperature());
        root.put("max_tokens", request.maxOutputTokens());

        ArrayNode messages = root.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", ROLE_SYSTEM);
        system.put("content", systemMessage(request));

        ObjectNode user = messages.addObject();
        user.put("role", ROLE_USER);
        // Nothing but the untrusted block. Never appended to the system message.
        user.put("content", request.untrustedContent());

        return root.toString();
    }

    /** Everything we wrote ourselves, and nothing that came from the analyzed project. */
    private static String systemMessage(LLMRequest request) {
        return "You are " + request.role() + ".\n\n"
                + request.instructions() + "\n\n"
                + request.expectedResponseFormat();
    }

    private HttpResponse<String> send(HttpRequest httpRequest, LLMRequest request) throws LLMException {
        try {
            return httpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException timeout) {
            // Covers both connect and response timeouts; retrying is worthwhile.
            throw new LLMException(LLMException.Kind.TIMEOUT,
                    "provider '" + describe() + "' did not answer within " + requestTimeout()
                            + " for criterion '" + request.criterionId() + "'",
                    timeout);
        } catch (IOException unreachable) {
            // Connection refused, DNS failure, TLS problem, socket reset before any response.
            throw new LLMException(LLMException.Kind.UNAVAILABLE,
                    "provider '" + describe() + "' is unreachable: "
                            + redactSecrets(String.valueOf(unreachable.getMessage())),
                    unreachable);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            // Reported as transient, but the restored interrupt flag means the resilience
            // decorator's own interrupt handling takes over almost immediately.
            throw new LLMException(LLMException.Kind.UNAVAILABLE,
                    "interrupted while waiting for provider '" + describe() + "'", interrupted);
        }
    }

    private LLMResponse toLLMResponse(HttpResponse<String> response, LLMRequest request)
            throws LLMException {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw statusFailure(status, response.body(), request);
        }

        JsonNode root = parseBody(response.body());
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new LLMException(LLMException.Kind.MALFORMED_RESPONSE,
                    "provider '" + describe() + "' returned no choices: " + snippet(response.body()));
        }

        JsonNode message = choices.get(0).get("message");
        if (message == null || !message.isObject()) {
            throw new LLMException(LLMException.Kind.MALFORMED_RESPONSE,
                    "provider '" + describe() + "' returned a choice without a message: "
                            + snippet(response.body()));
        }

        JsonNode content = message.get("content");
        if (content == null || !content.isTextual() || content.asText().isBlank()) {
            throw new LLMException(LLMException.Kind.EMPTY_RESPONSE,
                    "provider '" + describe() + "' returned an empty answer for criterion '"
                            + request.criterionId() + "'");
        }

        // Prefer the model the server says answered: it may differ from the one asked for (an
        // alias, a fallback model on the vendor's side), and the report should name what ran.
        JsonNode answeringModel = root.get("model");
        String model = answeringModel != null && answeringModel.isTextual() && !answeringModel.asText().isBlank()
                ? answeringModel.asText()
                : model();

        // fromFallback is false: this provider answered as the primary it was asked to be. Only
        // ResilientLLMProvider knows whether it was reached as somebody's fallback.
        return new LLMResponse(content.asText(), model, false);
    }

    /**
     * Maps a non-2xx status onto a kind, at the granularity
     * {@link ResilientLLMProvider} needs: transient conditions become retryable, permanent ones do
     * not, so a rejected key is not hammered three more times.
     */
    private LLMException statusFailure(int status, String body, LLMRequest request) {
        boolean transientFailure = status == 429 || status >= 500;
        LLMException.Kind kind =
                transientFailure ? LLMException.Kind.UNAVAILABLE : LLMException.Kind.HTTP_ERROR;

        LOG.warn("Provider '{}' answered HTTP {} for criterion '{}' -> {}",
                describe(), status, request.criterionId(), kind);

        return new LLMException(kind,
                "provider '" + describe() + "' answered HTTP " + status + ": " + snippet(body));
    }

    private JsonNode parseBody(String body) throws LLMException {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            throw new LLMException(LLMException.Kind.MALFORMED_RESPONSE,
                    "provider '" + describe() + "' answered with invalid JSON: " + snippet(body), e);
        }
    }

    /** Length-capped, single-line, secret-free quote of a response body, safe for logs. */
    private String snippet(String body) {
        if (body == null || body.isBlank()) {
            return "<empty body>";
        }
        String flattened = redactSecrets(body.replaceAll("\\s+", " ").strip());
        if (flattened.length() <= SNIPPET_LIMIT) {
            return '"' + flattened + '"';
        }
        return '"' + flattened.substring(0, SNIPPET_LIMIT) + "\" (truncated, " + body.length() + " chars)";
    }

    /**
     * Removes the API key from text on its way to a log or an exception message.
     *
     * <p>We never put the key there ourselves, so this is the second line of defense: a
     * misconfigured or hostile server can echo a credential back inside its error body, and that
     * body is quoted in {@link #snippet(String)}. Cheap insurance against the one leak path that
     * does not depend on our own code being careful.
     *
     * @param text text about to be logged or put in an exception message; may be {@code null}
     * @return the same text with any configured API key replaced by a redaction marker;
     *         {@code null} in, {@code null} out
     */
    protected final String redactSecrets(String text) {
        Optional<String> key = apiKey();
        if (text == null || key.isEmpty() || key.get().isBlank()) {
            return text;
        }
        return text.replace(key.get(), "***REDACTED***");
    }

    /** Lazily built, then reused: each {@link HttpClient} owns threads, so one per provider. */
    private HttpClient httpClient() {
        HttpClient client = httpClient;
        if (client == null) {
            synchronized (this) {
                client = httpClient;
                if (client == null) {
                    client = HttpClient.newBuilder()
                            .connectTimeout(requestTimeout())
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                    httpClient = client;
                }
            }
        }
        return client;
    }
}
