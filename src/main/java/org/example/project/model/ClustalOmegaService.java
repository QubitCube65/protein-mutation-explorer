package org.example.project.model;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ClustalOmegaService {

    private static final String BASE_URL =
        "https://www.ebi.ac.uk/Tools/services/rest/clustalo";
    private static final int MAX_POLL_SECONDS = 300;
    private static final int POLL_INTERVAL_MS = 3_000;

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(String message);
    }

    public AlignmentResult align(String fastaInput, ProgressCallback callback)
            throws IOException, InterruptedException {

        callback.onProgress("Submitting to EBI Clustal Omega…");
        String jobId = submitJob(fastaInput);
        System.out.println("[ClustalO] Submitted, jobId=" + jobId);

        Thread.sleep(2_000);

        long start = System.currentTimeMillis();
        while (true) {
            long elapsed = (System.currentTimeMillis() - start) / 1000;
            if (elapsed > MAX_POLL_SECONDS) {
                throw new IOException(
                    "Clustal Omega timed out after " + MAX_POLL_SECONDS + "s");
            }

            String status = checkStatus(jobId);
            System.out.println("[ClustalO] Status=" + status + " (" + elapsed + "s)");

            if ("FINISHED".equals(status)) {
                callback.onProgress("Retrieving alignment…");
                break;
            }
            if ("ERROR".equals(status) || "FAILURE".equals(status)
                    || "NOT_FOUND".equals(status)) {
                throw new IOException(
                    "Clustal Omega job failed (status: " + status + ")");
            }

            callback.onProgress("Alignment running… (" + elapsed + "s elapsed)");
            Thread.sleep(POLL_INTERVAL_MS);
        }

        String alignedFasta = getResult(jobId);
        return parseAlignedFasta(alignedFasta);
    }

    // ── Submit ────────────────────────────────────────────────────────────

    private String submitJob(String fastaInput) throws IOException {
        String params = "email="
            + URLEncoder.encode("protmutexplorer@example.com", StandardCharsets.UTF_8)
            + "&sequence=" + URLEncoder.encode(fastaInput, StandardCharsets.UTF_8)
            + "&stype=protein";

        HttpURLConnection conn = openPost(BASE_URL + "/run");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(params.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        String response = readFully(conn).trim();

        if (status != 200) {
            throw new IOException(
                "Clustal Omega submission failed (HTTP " + status + "): " + response);
        }
        return response;
    }

    // ── Poll ──────────────────────────────────────────────────────────────

    private String checkStatus(String jobId) throws IOException {
        HttpURLConnection conn = openGet(BASE_URL + "/status/" + jobId);
        return readFully(conn).trim();
    }

    // ── Retrieve ──────────────────────────────────────────────────────────

    private String getResult(String jobId) throws IOException {
        HttpURLConnection conn = openGet(BASE_URL + "/result/" + jobId + "/fa");
        return readFully(conn);
    }

    // ── Parse aligned FASTA ───────────────────────────────────────────────

    private AlignmentResult parseAlignedFasta(String fasta) throws IOException {
        List<String> names = new ArrayList<>();
        List<String> sequences = new ArrayList<>();
        StringBuilder current = null;

        for (String line : fasta.split("\n")) {
            line = line.trim();
            if (line.startsWith(">")) {
                if (current != null) sequences.add(current.toString());
                names.add(line.substring(1).trim());
                current = new StringBuilder();
            } else if (!line.isEmpty() && current != null) {
                current.append(line);
            }
        }
        if (current != null) sequences.add(current.toString());

        if (names.isEmpty()) {
            throw new IOException("Clustal Omega returned no aligned sequences");
        }
        return new AlignmentResult(names, sequences);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────

    private HttpURLConnection openPost(String urlStr) throws IOException {
        HttpURLConnection conn = open(urlStr);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type",
            "application/x-www-form-urlencoded");
        return conn;
    }

    private HttpURLConnection openGet(String urlStr) throws IOException {
        return open(urlStr);
    }

    private HttpURLConnection open(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);
        conn.setRequestProperty("User-Agent", "ProteinMutationExplorer/1.0");
        return conn;
    }

    private String readFully(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream stream = (status >= 200 && status < 300)
            ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) {
            throw new IOException("EBI returned HTTP " + status + " with no body");
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }
}
