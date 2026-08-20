package org.example.project.model.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Supplies the course OpenAI proxy key without ever hard-coding it into the
 * source (the key must not be committed to GitHub).
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Environment variable {@code OPENAI_PROXY_KEY}.</li>
 *   <li>A local file {@code openai-proxy.key} in the working directory
 *       (git-ignored). Blank lines and lines starting with {@code '#'} are
 *       skipped; the first remaining non-blank line is used as the key.</li>
 * </ol>
 */
public final class ProxyKeyProvider {

    public static final String ENV_VAR  = "OPENAI_PROXY_KEY";
    public static final String KEY_FILE = "openai-proxy.key";

    private ProxyKeyProvider() {}

    /**
     * @return the proxy key, trimmed.
     * @throws IllegalStateException if no key can be found in the env var or file.
     */
    public static String getKey() {
        String env = System.getenv(ENV_VAR);
        if (env != null && !env.isBlank()) return env.trim();

        Path file = Path.of(KEY_FILE);
        if (Files.isReadable(file)) {
            try {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    return trimmed;
                }
            } catch (IOException e) {
                throw new IllegalStateException(
                    "Could not read proxy key file '" + KEY_FILE + "': " + e.getMessage(), e);
            }
        }

        throw new IllegalStateException(
            "No OpenAI proxy key found. Set the environment variable '" + ENV_VAR
            + "' or paste your key into the file '" + KEY_FILE + "' in the project root.");
    }

    /** @return {@code true} if a key is configured (env var or file), without throwing. */
    public static boolean isConfigured() {
        try {
            getKey();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
