package org.example.project.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable protein structure: all atoms and residues parsed from PDB data. */
public final class ProteinStructure {
    private final String pdbId;
    private final List<Atom> atoms;
    private final List<AminoAcid> aminoAcids;

    public ProteinStructure(String pdbId, List<Atom> atoms, List<AminoAcid> aminoAcids) {
        this.pdbId      = Objects.requireNonNull(pdbId);
        this.atoms      = Collections.unmodifiableList(Objects.requireNonNull(atoms));
        this.aminoAcids = Collections.unmodifiableList(Objects.requireNonNull(aminoAcids));
    }

    public String getPdbId()            { return pdbId; }
    public List<Atom> getAtoms()        { return atoms; }
    public List<AminoAcid> getAminoAcids() { return aminoAcids; }
    public int getAtomCount()           { return atoms.size(); }
    public int getResidueCount()        { return aminoAcids.size(); }

    @Override public String toString() {
        return "ProteinStructure{id='" + pdbId + "', atoms=" + atoms.size() + ", residues=" + aminoAcids.size() + "}";
    }
}
