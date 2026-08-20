package org.example.project.model;

import javafx.scene.paint.Color;
import java.util.Map;

/** CPK (Corey-Pauling-Koltun) color scheme for atom elements. */
public final class CPKColorService {

    private static final Map<String, Color> COLORS = Map.ofEntries(
        Map.entry("H",  Color.web("#FFFFFF")), Map.entry("C",  Color.web("#909090")),
        Map.entry("N",  Color.web("#3050F8")), Map.entry("O",  Color.web("#FF0D0D")),
        Map.entry("S",  Color.web("#FFFF30")), Map.entry("P",  Color.web("#FF7F00")),
        Map.entry("F",  Color.web("#90E050")), Map.entry("Cl", Color.web("#1FF01F")),
        Map.entry("Br", Color.web("#A62929")), Map.entry("I",  Color.web("#940094"))
    );

    public Color colorFor(Atom atom) {
        String e = atom.getElement();
        if (e == null || e.isEmpty()) return Color.web("#CCCCCC");
        String key = e.substring(0, 1).toUpperCase() + (e.length() > 1 ? e.substring(1).toLowerCase() : "");
        return COLORS.getOrDefault(key, Color.web("#FF00FF")); // magenta for unknown elements
    }
}
