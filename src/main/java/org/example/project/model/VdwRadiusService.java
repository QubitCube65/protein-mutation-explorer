package org.example.project.model;

import java.util.Map;

/** Van-der-Waals radii for atom sphere sizing. */
public final class VdwRadiusService {

    private static final Map<String, Double> RADII = Map.ofEntries(
        Map.entry("H",  1.20), Map.entry("C",  1.70), Map.entry("N",  1.55),
        Map.entry("O",  1.52), Map.entry("S",  1.80), Map.entry("P",  1.80),
        Map.entry("F",  1.47), Map.entry("Cl", 1.75), Map.entry("Br", 1.85),
        Map.entry("I",  1.98)
    );

    public double radiusFor(Atom atom) {
        String e = atom.getElement();
        if (e == null || e.isEmpty()) return 1.70;
        String key = e.substring(0, 1).toUpperCase() + (e.length() > 1 ? e.substring(1).toLowerCase() : "");
        return RADII.getOrDefault(key, 1.70);
    }
}
