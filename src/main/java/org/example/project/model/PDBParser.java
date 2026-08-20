package org.example.project.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.logging.Logger;

/** Parses PDB-format text into Atom / AminoAcid / ProteinStructure objects. */
public final class PDBParser {

    private static final Logger LOG = Logger.getLogger(PDBParser.class.getName());

    private int lastParseWarnings;

    public int getLastParseWarnings() { return lastParseWarnings; }

    public ProteinStructure parseContent(String pdbId, String content) throws IOException {
        List<Atom> atoms = new ArrayList<>();
        Map<String, List<Atom>> residueAtoms = new LinkedHashMap<>();
        int skippedLines = 0;

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.length() < 6) continue;
                String record = line.substring(0, 6).trim();
                if (!record.equals("ATOM") && !record.equals("HETATM")) continue;
                try {
                    Atom atom = parseAtomLine(line, record.equals("HETATM"));
                    atoms.add(atom);
                    residueAtoms
                        .computeIfAbsent(atom.getChainId() + ":" + atom.getResidueSequenceNumber(),
                                         k -> new ArrayList<>())
                        .add(atom);
                } catch (Exception e) {
                    skippedLines++;
                    LOG.warning("Skipped malformed ATOM/HETATM at line " + lineNumber + ": " + e.getMessage());
                }
            }
        }

        lastParseWarnings = skippedLines;
        if (skippedLines > 0) {
            LOG.info("PDB parse complete: " + skippedLines + " malformed line(s) skipped");
        }

        List<AminoAcid> aminoAcids = new ArrayList<>();
        for (Map.Entry<String, List<Atom>> entry : residueAtoms.entrySet()) {
            String[] parts = entry.getKey().split(":");
            char chainId = parts[0].charAt(0);
            int seqNum   = Integer.parseInt(parts[1]);
            List<Atom> ats = entry.getValue();
            if (ats.isEmpty()) continue;
            boolean hetatm = ats.stream().anyMatch(Atom::isHetatm);
            aminoAcids.add(new AminoAcid(ats.get(0).getResidueName(), chainId, seqNum, ats, hetatm));
        }

        return new ProteinStructure(pdbId, atoms, aminoAcids);
    }

    private Atom parseAtomLine(String line, boolean hetatm) {
        int serial    = Integer.parseInt(line.substring(6, 11).trim());
        String name   = line.substring(12, 16).trim();
        String resName = line.substring(17, 20).trim();
        char chainId  = line.charAt(21);
        int resSeq    = Integer.parseInt(line.substring(22, 26).trim());
        double x      = Double.parseDouble(line.substring(30, 38).trim());
        double y      = Double.parseDouble(line.substring(38, 46).trim());
        double z      = Double.parseDouble(line.substring(46, 54).trim());
        double occ    = Double.parseDouble(line.substring(54, 60).trim());
        double temp   = Double.parseDouble(line.substring(60, 66).trim());
        String elem   = line.length() > 76 ? line.substring(76, Math.min(78, line.length())).trim() : "";
        if (elem.isEmpty()) elem = name.replaceAll("[0-9]", "");
        return new Atom(serial, name, resName, chainId, resSeq, x, y, z, occ, temp, elem, hetatm);
    }
}
