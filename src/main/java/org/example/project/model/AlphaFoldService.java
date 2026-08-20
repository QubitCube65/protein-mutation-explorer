package org.example.project.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a precomputed structure from the public AlphaFold Protein Structure
 * Database ({@code alphafold.ebi.ac.uk}) by UniProt accession.
 *
 * <p>Unlike {@link ESMFoldService}, this does <em>not</em> run a fold - it downloads
 * an already-computed PDB file, so it is fast, free, needs no key, and has no length
 * limit. The catch: only sequences that already exist in UniProt / AlphaFold DB are
 * available; edited sequences are not. This is a blocking call - always invoke it
 * from a background thread.
 *
 * <p>The exact file URL (and its model version, currently v6) is not hard-coded:
 * we first ask the AlphaFold prediction API for the entry and read its {@code pdbUrl},
 * so the code keeps working as the database is re-versioned.
 */
public final class AlphaFoldService {

    private static final String API_TEMPLATE =
        "https://alphafold.ebi.ac.uk/api/prediction/%s";

    // Matches a "sp|P05067|NAME" / "tr|..." style header first…
    private static final Pattern PIPE_ACCESSION =
        Pattern.compile("(?:sp|tr)\\|([A-Z0-9]+)\\|", Pattern.CASE_INSENSITIVE);
    // …otherwise falls back to the official UniProt accession pattern anywhere in the header.
    private static final Pattern UNIPROT_ACCESSION = Pattern.compile(
        "[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2}");

    public String fetchByAccession(String accession) throws IOException {
        // 1) Ask the API for this entry and read the current pdbUrl.
        String json;
        try {
            json = httpGet(String.format(API_TEMPLATE, accession), "application/json");
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                throw new IOException("No AlphaFold DB entry found for accession " + accession + ".");
            }
            throw e;
        }

        String pdbUrl;
        try {
            JsonArray entries = JsonParser.parseString(json).getAsJsonArray();
            if (entries.isEmpty()) {
                throw new IOException("No AlphaFold DB entry found for accession " + accession + ".");
            }
            pdbUrl = entries.get(0).getAsJsonObject().get("pdbUrl").getAsString();
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Unexpected AlphaFold API response for " + accession + ".");
        }

        // 2) Download the PDB file itself.
        String pdb = httpGet(pdbUrl, "chemical/x-pdb, text/plain");
        if (!pdb.contains("ATOM")) {
            throw new IOException("AlphaFold DB response for " + accession
                + " does not look like PDB data.");
        }
        return pdb;
    }

    /**
     * Extracts a UniProt accession from a FASTA header, or {@code null} if none is found.
     * Handles {@code sp|P05067|A4_HUMAN} style headers as well as a bare accession.
     */
    public static String parseAccession(String fastaHeader) {
        if (fastaHeader == null || fastaHeader.isBlank()) return null;
        Matcher pipe = PIPE_ACCESSION.matcher(fastaHeader);
        if (pipe.find()) return pipe.group(1).toUpperCase();
        Matcher generic = UNIPROT_ACCESSION.matcher(fastaHeader.toUpperCase());
        return generic.find() ? generic.group() : null;
    }

    private static String httpGet(String url, String accept) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setRequestProperty("Accept", accept);

        int status = conn.getResponseCode();
        InputStream stream = (status >= 200 && status < 300)
            ? conn.getInputStream() : conn.getErrorStream();
        String body = readBody(stream);
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " from " + url);
        }
        return body;
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
}
