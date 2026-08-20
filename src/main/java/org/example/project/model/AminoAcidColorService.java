package org.example.project.model;

/**
 * Maps each amino acid to a colour by physicochemical group (ClustalX-inspired,
 * tuned for a dark background). Used to colour the letters in the alignment view.
 * Returns hex colour strings so the model layer stays free of JavaFX types.
 */
public final class AminoAcidColorService {

    private static final String DEFAULT = "#CDD9E5";

    private AminoAcidColorService() {}

    /** @return the physicochemical group name for the residue (for grouping/legends). */
    public static String groupOf(char residue) {
        return switch (Character.toUpperCase(residue)) {
            case 'A', 'V', 'L', 'I', 'M' -> "Hydrophobic";
            case 'F', 'W', 'Y'           -> "Aromatic";
            case 'K', 'R', 'H'           -> "Positive";
            case 'D', 'E'                -> "Negative";
            case 'S', 'T', 'N', 'Q'      -> "Polar";
            case 'C'                     -> "Cysteine";
            case 'G', 'P'                -> "Special";
            default                      -> "Other";
        };
    }

    /** @return the hex colour for a group name produced by {@link #groupOf(char)}. */
    public static String groupColor(String group) {
        return switch (group) {
            case "Hydrophobic" -> "#5B9BFF";
            case "Aromatic"    -> "#2CC9B8";
            case "Positive"    -> "#FF6B6B";
            case "Negative"    -> "#E06BFF";
            case "Polar"       -> "#5FD37A";
            case "Cysteine"    -> "#F5D76E";
            case "Special"     -> "#F0A64E";
            default            -> DEFAULT;
        };
    }

    /** @return a hex colour for the residue, grouped by chemical property. */
    public static String colorOf(char residue) {
        return switch (Character.toUpperCase(residue)) {
            // Hydrophobic / aliphatic
            case 'A', 'V', 'L', 'I', 'M' -> "#5B9BFF";
            // Aromatic
            case 'F', 'W', 'Y'           -> "#2CC9B8";
            // Positively charged
            case 'K', 'R', 'H'           -> "#FF6B6B";
            // Negatively charged
            case 'D', 'E'                -> "#E06BFF";
            // Polar uncharged
            case 'S', 'T', 'N', 'Q'      -> "#5FD37A";
            // Cysteine (disulfide-forming)
            case 'C'                     -> "#F5D76E";
            // Special / conformationally distinct
            case 'G', 'P'                -> "#F0A64E";
            default                      -> DEFAULT;   // unknown, X, stop, etc.
        };
    }
}
