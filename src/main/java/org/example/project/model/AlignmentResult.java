package org.example.project.model;

import java.util.List;

public final class AlignmentResult {

    private final List<String> names;
    private final List<String> alignedSequences;

    public AlignmentResult(List<String> names, List<String> alignedSequences) {
        this.names            = List.copyOf(names);
        this.alignedSequences = List.copyOf(alignedSequences);
    }

    public int getSequenceCount() { return names.size(); }

    public int getAlignmentLength() {
        return alignedSequences.isEmpty() ? 0 : alignedSequences.get(0).length();
    }

    public String getName(int row)            { return names.get(row); }
    public String getAlignedSequence(int row) { return alignedSequences.get(row); }
    public char   getChar(int row, int col)   { return alignedSequences.get(row).charAt(col); }

    public List<String> getNames()            { return names; }
    public List<String> getAlignedSequences() { return alignedSequences; }
}
