package org.example.project.model.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Thin transport wrapper around the course OpenAI API proxy
 * ({@code http://134.2.9.179/v1/chat/completions}), used exactly like the
 * OpenAI Chat Completions API.
 *
 * <p>Its responsibility is deliberately limited to <em>transport</em>: it sends a
 * system prompt plus a user prompt, requests a JSON-object response, and returns
 * the raw assistant message content. It has no knowledge of sequence edits or
 * their validation - callers turn the returned JSON into domain objects and
 * validate them separately.
 *
 * <p>This is a blocking call - always invoke it from a background thread.
 */
public final class OpenAIService {

    private static final String PROXY_URL =
        "http://134.2.9.179/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS    = 60_000;

    private final String model;

    public OpenAIService()             { this(DEFAULT_MODEL); }
    public OpenAIService(String model) { this.model = model; }

    /**
     * Sends a chat-completion request and returns the assistant's message content.
     *
     * @param systemPrompt instruction / schema description for the model
     * @param userPrompt   the user request plus context (e.g. the current sequence)
     * @return the assistant message content - expected to be a JSON string, since
     *         {@code response_format=json_object} is requested
     * @throws IOException on transport error or a non-200 response
     */
    public String complete(String systemPrompt, String userPrompt) throws IOException {
        String requestBody = buildRequestBody(systemPrompt, userPrompt);

        HttpURLConnection conn = (HttpURLConnection) new URL(PROXY_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + ProxyKeyProvider.getKey());
        conn.setRequestProperty("Connection", "close");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        String response = readBody(status == 200 ? conn.getInputStream() : conn.getErrorStream());

        if (status != 200) {
            throw new IOException("OpenAI proxy returned HTTP " + status + ": "
                + truncate(response, 300));
        }
        return extractContent(response);
    }

    // ── Request building ───────────────────────────────────────────────────

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", systemPrompt);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userPrompt);

        JsonArray messages = new JsonArray();
        messages.add(sys);
        messages.add(user);

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.add("response_format", responseFormat);
        body.addProperty("temperature", 0);      // deterministic, structured output
        body.addProperty("max_tokens", 2000);    // the course proxy caps max_tokens at 2000/request
        return body.toString();
    }

    // ── Response parsing (envelope only) ───────────────────────────────────

    /** Pulls {@code choices[0].message.content} out of the chat-completions envelope. */
    private String extractContent(String responseJson) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
            if (root.has("error") && !root.get("error").isJsonNull()) {
                throw new IOException("OpenAI proxy error: "
                    + root.getAsJsonObject("error").get("message").getAsString());
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("OpenAI response had no choices: " + truncate(responseJson, 300));
            }
            JsonObject choice = choices.get(0).getAsJsonObject();
            // A "length" finish means the model hit the token cap and the JSON is cut off.
            if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                    && "length".equals(choice.get("finish_reason").getAsString())) {
                throw new IOException("The AI response was cut off because it reached the length "
                    + "limit. Try a smaller change (for example, insert fewer residues).");
            }
            return choice.getAsJsonObject("message").get("content").getAsString();
        } catch (JsonParseException | IllegalStateException | NullPointerException e) {
            throw new IOException("Could not parse OpenAI response: " + e.getMessage()
                + " - body: " + truncate(responseJson, 300), e);
        }
    }

    private static String readBody(InputStream in) throws IOException {
        if (in == null) return "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
