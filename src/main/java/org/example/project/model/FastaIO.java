package org.example.project.model;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Reads and writes FASTA format files. */
public final class FastaIO {

    private FastaIO() {}

    /** Loads the first sequence from a FASTA file. */
    public static Sequence loadFromFile(File file) throws IOException {
        String header = "";
        StringBuilder seq = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith(">")) {
                    if (!seq.isEmpty()) break; // stop at second entry
                    header = line.substring(1).trim();
                } else if (!line.isEmpty() && !line.startsWith(";")) {
                    seq.append(line);
                }
            }
        }
        if (seq.isEmpty()) throw new IOException("No sequence found in FASTA file.");
        return new Sequence(header, seq.toString());
    }

    /** Writes a sequence to a FASTA file (60 residues per line). */
    public static void saveToFile(Sequence sequence, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            String hdr = sequence.getHeader().isBlank() ? "sequence" : sequence.getHeader();
            writer.println(">" + hdr);
            String seq = sequence.toSequenceString();
            for (int i = 0; i < seq.length(); i += 60) {
                writer.println(seq.substring(i, Math.min(i + 60, seq.length())));
            }
        }
    }
}
