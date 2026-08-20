package org.example.project.model;

import java.util.Objects;

/** Covalent bond between two atoms. Pure data. */
public final class Bond {
    private final Atom first;
    private final Atom second;

    public Bond(Atom first, Atom second) {
        this.first  = Objects.requireNonNull(first);
        this.second = Objects.requireNonNull(second);
    }

    public Atom getFirst()  { return first; }
    public Atom getSecond() { return second; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bond b)) return false;
        return (Objects.equals(first, b.first) && Objects.equals(second, b.second)) ||
               (Objects.equals(first, b.second) && Objects.equals(second, b.first));
    }
    @Override public int hashCode() { return Objects.hash(first.getSerial(), second.getSerial()); }
    @Override public String toString() { return "Bond{" + first.getSerial() + "-" + second.getSerial() + "}"; }
}
