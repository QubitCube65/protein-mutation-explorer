package org.example.project.model;

import java.util.Objects;

/** One atom from a PDB record. Pure data, no JavaFX. */
public final class Atom {
    private final int serial;
    private final String name;
    private final String residueName;
    private final char chainId;
    private final int residueSequenceNumber;
    private final double x, y, z;
    private final double occupancy;
    private final double temperatureFactor;
    private final String element;
    private final boolean hetatm;

    public Atom(int serial, String name, String residueName, char chainId,
                int residueSequenceNumber, double x, double y, double z,
                double occupancy, double temperatureFactor, String element, boolean hetatm) {
        this.serial = serial;
        this.name = Objects.requireNonNull(name);
        this.residueName = Objects.requireNonNull(residueName);
        this.chainId = chainId;
        this.residueSequenceNumber = residueSequenceNumber;
        this.x = x;
        this.y = y;
        this.z = z;
        this.occupancy = occupancy;
        this.temperatureFactor = temperatureFactor;
        this.element = Objects.requireNonNull(element);
        this.hetatm = hetatm;
    }

    public int getSerial()                  { return serial; }
    public String getName()                 { return name; }
    public String getResidueName()          { return residueName; }
    public char getChainId()                { return chainId; }
    public int getResidueSequenceNumber()   { return residueSequenceNumber; }
    public double getX()                    { return x; }
    public double getY()                    { return y; }
    public double getZ()                    { return z; }
    public double getOccupancy()            { return occupancy; }
    public double getTemperatureFactor()    { return temperatureFactor; }
    public String getElement()              { return element; }
    public boolean isHetatm()               { return hetatm; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Atom a)) return false;
        return serial == a.serial;
    }
    @Override public int hashCode() { return Objects.hash(serial); }
    @Override public String toString() { return "Atom{serial=" + serial + ", name='" + name + "'}"; }
}
