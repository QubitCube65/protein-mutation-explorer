package org.example.project.model;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public final class BlastService {

    private static final String BLAST_URL =
        "https://blast.ncbi.nlm.nih.gov/blast/Blast.cgi";
    private static final int MAX_POLL_SECONDS = 600;
    private static final int POLL_INTERVAL_MS = 5_000;

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(String message);
    }

    private record SubmitResult(String rid, int rtoe) {}

    public List<BlastHit> search(String sequence, int maxHits,
                                 ProgressCallback callback)
            throws IOException, InterruptedException {

        callback.onProgress("Submitting to NCBI BLAST (swissprot)…");
        SubmitResult submit = submitSearch(sequence, maxHits);
        System.out.println("[BLAST] Submitted RID=" + submit.rid()
            + ", RTOE=" + submit.rtoe() + "s");

        int waitSec = Math.max(5, Math.min(submit.rtoe(), 60));
        callback.onProgress("BLAST job queued - server estimates ~"
            + submit.rtoe() + "s, waiting " + waitSec + "s before first check…");
        Thread.sleep(waitSec * 1000L);

        long start = System.currentTimeMillis();
        while (true) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Cancelled");

            long elapsed = (System.currentTimeMillis() - start) / 1000;
            if (elapsed > MAX_POLL_SECONDS)
                throw new IOException("BLAST timed out after " + MAX_POLL_SECONDS + "s");

            String status = checkStatus(submit.rid());
            System.out.println("[BLAST] Status=" + status + " (" + elapsed + "s)");

            if ("READY".equals(status)) {
                callback.onProgress("BLAST finished - retrieving results…");
                break;
            }
            if ("FAILED".equals(status) || "UNKNOWN".equals(status))
                throw new IOException("BLAST search failed on server (status: " + status + ")");

            long total = elapsed + waitSec;
            callback.onProgress("BLAST running on NCBI server… ("
                + total + "s elapsed, checking every " + (POLL_INTERVAL_MS/1000) + "s)");

            Thread.sleep(POLL_INTERVAL_MS);
        }

        String xml = getResults(submit.rid());
        return parseBlastXml(xml);
    }

    // ── Submit ────────────────────────────────────────────────────────────

    private SubmitResult submitSearch(String sequence, int maxHits) throws IOException {
        String params = "CMD=Put"
            + "&PROGRAM=blastp"
            + "&DATABASE=swissprot"
            + "&QUERY=" + URLEncoder.encode(sequence, StandardCharsets.UTF_8)
            + "&HITLIST_SIZE=" + maxHits
            + "&EXPECT=0.01"
            + "&WORD_SIZE=6"
            + "&FORMAT_TYPE=XML"
            + "&TOOL=ProteinMutationExplorer";

        HttpURLConnection conn = openPost(BLAST_URL);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(params.getBytes(StandardCharsets.UTF_8));
        }

        String response = readFully(conn);

        Matcher ridM = Pattern.compile("RID = (\\S+)").matcher(response);
        if (!ridM.find())
            throw new IOException("Could not parse RID from BLAST response - server may be down");

        Matcher rtoeM = Pattern.compile("RTOE = (\\d+)").matcher(response);
        int rtoe = rtoeM.find() ? Integer.parseInt(rtoeM.group(1)) : 15;

        return new SubmitResult(ridM.group(1), rtoe);
    }

    // ── Poll ──────────────────────────────────────────────────────────────

    private String checkStatus(String rid) throws IOException {
        String url = BLAST_URL + "?CMD=Get&FORMAT_OBJECT=SearchInfo&RID=" + rid;
        HttpURLConnection conn = openGet(url);
        String response = readFully(conn);
        Matcher m = Pattern.compile("Status=(\\S+)").matcher(response);
        return m.find() ? m.group(1) : "UNKNOWN";
    }

    // ── Retrieve ──────────────────────────────────────────────────────────

    private String getResults(String rid) throws IOException {
        String url = BLAST_URL + "?CMD=Get&FORMAT_TYPE=XML&RID=" + rid;
        HttpURLConnection conn = openGet(url);
        return readFully(conn);
    }

    // ── Parse XML ─────────────────────────────────────────────────────────

    private List<BlastHit> parseBlastXml(String xml) throws IOException {
        if (!xml.contains("<BlastOutput>"))
            throw new IOException(
                "BLAST response is not valid XML - server may be temporarily unavailable.");

        List<BlastHit> hits = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList hitNodes = doc.getElementsByTagName("Hit");
            for (int i = 0; i < hitNodes.getLength(); i++) {
                Element hit = (Element) hitNodes.item(i);
                String accession = tagText(hit, "Hit_accession");
                String def       = tagText(hit, "Hit_def");

                NodeList hspNodes = hit.getElementsByTagName("Hsp");
                if (hspNodes.getLength() == 0) continue;
                Element hsp = (Element) hspNodes.item(0);

                String hseq   = tagText(hsp, "Hsp_hseq").replace("-", "");
                double eValue = Double.parseDouble(tagText(hsp, "Hsp_evalue"));
                int identity  = Integer.parseInt(tagText(hsp, "Hsp_identity"));
                int alignLen  = Integer.parseInt(tagText(hsp, "Hsp_align-len"));
                double idPct  = alignLen > 0 ? identity * 100.0 / alignLen : 0;

                hits.add(new BlastHit(accession, def, hseq, eValue, idPct));
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse BLAST XML: " + e.getMessage(), e);
        }
        return hits;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────

    private static String tagText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() > 0 ? list.item(0).getTextContent().trim() : "";
    }

    private HttpURLConnection openPost(String urlStr) throws IOException {
        HttpURLConnection conn = open(urlStr);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
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
        if (stream == null)
            throw new IOException("NCBI returned HTTP " + status + " with no body");
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }
}
