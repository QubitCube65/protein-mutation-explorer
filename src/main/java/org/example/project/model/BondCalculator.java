package org.example.project.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Detects covalent bonds by comparing atom-pair distances against summed covalent radii. */
public final class BondCalculator {

    private static final Map<String, Double> COVALENT_RADII = Map.ofEntries(
        Map.entry("H",  0.31), Map.entry("C",  0.76), Map.entry("N",  0.71),
        Map.entry("O",  0.66), Map.entry("S",  1.05), Map.entry("P",  1.07),
        Map.entry("F",  0.57), Map.entry("Cl", 1.02), Map.entry("Br", 1.20),
        Map.entry("I",  1.39)
    );

    public List<Bond> detectBonds(List<Atom> atoms) {
        List<Bond> bonds = new ArrayList<>();
        for (int i = 0; i < atoms.size(); i++) {
            for (int j = i + 1; j < atoms.size(); j++) {
                Atom a1 = atoms.get(i), a2 = atoms.get(j);
                if (distance(a1, a2) <= maxBondDistance(a1, a2))
                    bonds.add(new Bond(a1, a2));
            }
        }
        return bonds;
    }

    private double distance(Atom a, Atom b) {
        double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double maxBondDistance(Atom a, Atom b) {
        return radius(a.getElement()) + radius(b.getElement()) + 0.15;
    }

    private double radius(String element) {
        if (element == null || element.isEmpty()) return 0.70;
        String e = element.substring(0, 1).toUpperCase() +
                   (element.length() > 1 ? element.substring(1).toLowerCase() : "");
        return COVALENT_RADII.getOrDefault(e, 0.70);
    }
}
