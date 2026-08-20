package org.example.project.model;

import java.util.Map;

/** Maps single-letter amino acid codes to three-letter codes and full names. */
public final class AminoAcidNames {

    private AminoAcidNames() {}

    private static final Map<Character, String[]> NAMES = Map.ofEntries(
        Map.entry('A', new String[]{"Ala", "Alanine"}),
        Map.entry('C', new String[]{"Cys", "Cysteine"}),
        Map.entry('D', new String[]{"Asp", "Aspartic Acid"}),
        Map.entry('E', new String[]{"Glu", "Glutamic Acid"}),
        Map.entry('F', new String[]{"Phe", "Phenylalanine"}),
        Map.entry('G', new String[]{"Gly", "Glycine"}),
        Map.entry('H', new String[]{"His", "Histidine"}),
        Map.entry('I', new String[]{"Ile", "Isoleucine"}),
        Map.entry('K', new String[]{"Lys", "Lysine"}),
        Map.entry('L', new String[]{"Leu", "Leucine"}),
        Map.entry('M', new String[]{"Met", "Methionine"}),
        Map.entry('N', new String[]{"Asn", "Asparagine"}),
        Map.entry('P', new String[]{"Pro", "Proline"}),
        Map.entry('Q', new String[]{"Gln", "Glutamine"}),
        Map.entry('R', new String[]{"Arg", "Arginine"}),
        Map.entry('S', new String[]{"Ser", "Serine"}),
        Map.entry('T', new String[]{"Thr", "Threonine"}),
        Map.entry('V', new String[]{"Val", "Valine"}),
        Map.entry('W', new String[]{"Trp", "Tryptophan"}),
        Map.entry('Y', new String[]{"Tyr", "Tyrosine"}),
        Map.entry('*', new String[]{"***", "Stop Codon"})
    );

    public static String threeLetterCode(char oneLetter) {
        String[] entry = NAMES.get(Character.toUpperCase(oneLetter));
        return entry != null ? entry[0] : "???";
    }

    public static String fullName(char oneLetter) {
        String[] entry = NAMES.get(Character.toUpperCase(oneLetter));
        return entry != null ? entry[1] : "Unknown";
    }

    public static String tooltip(char oneLetter) {
        String[] entry = NAMES.get(Character.toUpperCase(oneLetter));
        if (entry == null) return oneLetter + " - Unknown";
        return entry[0] + " - " + entry[1];
    }
}
