package org.example.project.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable one-letter amino acid sequence with FASTA header.
 * Accepts all 20 standard residue codes plus '*' (stop codon).
 */
public final class Sequence {

    private final List<Character> residues = new ArrayList<>();
    private String header;

    public Sequence(String header, String sequence) {
        this.header = header;
        for (char c : sequence.toUpperCase().toCharArray()) {
            if (Character.isLetter(c) || c == '*') residues.add(c);
        }
    }

    public int length()             { return residues.size(); }
    public char get(int index)      { return residues.get(index); }
    public String getHeader()       { return header; }
    public void setHeader(String h) { this.header = h; }

    public boolean containsStopCodon() { return residues.contains('*'); }

    /** Full one-letter sequence including any stop codons. */
    public String toSequenceString() {
        StringBuilder sb = new StringBuilder(residues.size());
        for (char c : residues) sb.append(c);
        return sb.toString();
    }

    /** Sequence with stop codons stripped - suitable for submission to ESMFold. */
    public String toFoldableSequenceString() {
        StringBuilder sb = new StringBuilder(residues.size());
        for (char c : residues) { if (c != '*') sb.append(c); }
        return sb.toString();
    }

    /**
     * Replaces the residue at {@code index} with {@code newAA}.
     * Returns the previous residue so the caller can build an undo action.
     */
    public char mutate(int index, char newAA) {
        char old = residues.get(index);
        residues.set(index, Character.toUpperCase(newAA));
        return old;
    }

    /**
     * Inserts {@code aa} at position {@code index}, shifting later residues right.
     * Pass {@code index == length()} to append.
     */
    public void insert(int index, char aa) {
        residues.add(index, Character.toUpperCase(aa));
    }

    /**
     * Removes the residue at {@code index} and returns it.
     */
    public char delete(int index) {
        return residues.remove(index);
    }
}
