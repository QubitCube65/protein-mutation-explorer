package org.example.project.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One residue in a protein: name, chain, sequence number, and its atoms. */
public final class AminoAcid {
    private final String name;
    private final char chainId;
    private final int sequenceNumber;
    private final List<Atom> atoms;
    private final boolean hetero;

    public AminoAcid(String name, char chainId, int sequenceNumber, List<Atom> atoms, boolean hetero) {
        this.name = Objects.requireNonNull(name);
        this.chainId = chainId;
        this.sequenceNumber = sequenceNumber;
        this.atoms = Collections.unmodifiableList(Objects.requireNonNull(atoms));
        this.hetero = hetero;
    }

    public String getName()        { return name; }
    public char getChainId()       { return chainId; }
    public int getSequenceNumber() { return sequenceNumber; }
    public List<Atom> getAtoms()   { return atoms; }
    public boolean isHetero()      { return hetero; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AminoAcid a)) return false;
        return chainId == a.chainId && sequenceNumber == a.sequenceNumber;
    }
    @Override public int hashCode() { return Objects.hash(chainId, sequenceNumber); }
    @Override public String toString() { return "AminoAcid{" + name + ", chain=" + chainId + ", seq=" + sequenceNumber + "}"; }
}
