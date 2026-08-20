package org.example.project.model;

/** Immutable snapshot: the sequence that was folded, the resulting 3D structure, and a description. */
public final class Conformation {

    private final String sequence;
    private final ProteinStructure structure; // null if folding has not been run yet
    private final String description;

    public Conformation(String sequence, ProteinStructure structure, String description) {
        this.sequence    = sequence;
        this.structure   = structure;
        this.description = description;
    }

    public String getSequence()            { return sequence; }
    public ProteinStructure getStructure() { return structure; }
    public String getDescription()         { return description; }

    /** Short sequence preview for UI labels (max 10 chars). */
    public String shortLabel() {
        return sequence.length() > 10 ? sequence.substring(0, 10) + "…" : sequence;
    }
}
