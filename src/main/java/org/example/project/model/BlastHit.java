package org.example.project.model;

public final class BlastHit {

    private final String accession;
    private final String description;
    private final String sequence;
    private final double eValue;
    private final double identityPercent;

    public BlastHit(String accession, String description, String sequence,
                    double eValue, double identityPercent) {
        this.accession       = accession;
        this.description     = description;
        this.sequence        = sequence;
        this.eValue          = eValue;
        this.identityPercent = identityPercent;
    }

    public String getAccession()       { return accession; }
    public String getDescription()     { return description; }
    public String getSequence()        { return sequence; }
    public double getEValue()          { return eValue; }
    public double getIdentityPercent() { return identityPercent; }

    public String shortLabel() {
        String desc = description.length() > 40
            ? description.substring(0, 37) + "..."
            : description;
        return accession + " - " + desc;
    }

    @Override
    public String toString() {
        return accession + " | " + description
            + " (E=" + eValue + ", " + String.format("%.1f", identityPercent) + "% id)";
    }
}
