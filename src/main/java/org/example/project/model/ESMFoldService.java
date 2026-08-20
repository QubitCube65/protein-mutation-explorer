package org.example.project.model;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Submits an amino acid sequence to the public ESMFold API and returns PDB-format text.
 * Endpoint: POST https://esmatlas.com/resources/api/fold (form-encoded).
 * This is a blocking call - always invoke from a background thread.
 */
public final class ESMFoldService {

    // ESMFold public API - plain-text sequence as body
    private static final String API_URL = "https://api.esmatlas.com/foldSequence/v1/pdb";

    public String fold(String sequence) throws IOException {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(120_000); // folding can take up to 2 minutes
        conn.setRequestProperty("Content-Type", "text/plain");
        conn.setRequestProperty("Accept", "text/plain");
        conn.setRequestProperty("Connection", "close"); // prevent keep-alive reuse between retries

        // Body is the raw amino acid sequence (no encoding needed)
        try (OutputStream os = conn.getOutputStream()) {
            os.write(sequence.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        System.out.println("[ESMFold] HTTP status: " + status);

        InputStream stream = (status == 200) ? conn.getInputStream() : conn.getErrorStream();
        String response;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            response = sb.toString();
        }

        // Log the first 300 chars
        System.out.println("[ESMFold] Response (first 300 chars): "
            + response.substring(0, Math.min(300, response.length())).replace('\n', '|'));

        if (status != 200) {
            throw new IOException("ESMFold API returned HTTP " + status + ": " + response.substring(0, Math.min(200, response.length())));
        }

        // Sanity check: a valid PDB starts with ATOM/HETATM/REMARK lines
        if (!response.contains("ATOM") && !response.contains("HETATM")) {
            throw new IOException("ESMFold response does not look like PDB data: "
                + response.substring(0, Math.min(200, response.length())));
        }

        return response;
    }
}
