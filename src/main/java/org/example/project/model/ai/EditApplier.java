package org.example.project.model.ai;

import org.example.project.model.Sequence;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a list of <em>already validated</em> {@link SequenceEdit}s to a
 * sequence and returns a brand-new {@link Sequence} (the input is not modified).
 *
 * <p>All edit positions are interpreted against the <strong>original</strong>
 * sequence coordinates, exactly as the assignment specifies. Rather than mutating
 * in place - where each edit would shift the positions of the following ones - the
 * result is rebuilt in a single pass over the original residues, so substitutions,
 * insertions and deletions never interfere with one another's numbering.
 *
 * <p>Interplay at the same coordinate: a deletion removes the residue, a
 * substitution replaces it, and an insertion after position <i>p</i> is emitted
 * right after that slot regardless. If two substitutions target the same position,
 * the last one wins.
 */
public final class EditApplier {

    private EditApplier() {}

    public static Sequence apply(Sequence original, List<SequenceEdit> edits) {
        int n = original.length();

        Map<Integer, Character> substitutions = new HashMap<>();          // 1-based position -> new residue
        boolean[] deleted = new boolean[n + 1];                            // 1-based flag
        Map<Integer, StringBuilder> insertions = new LinkedHashMap<>();    // slot 0..n -> residues to insert

        for (SequenceEdit edit : edits) {
            if (edit instanceof SequenceEdit.Substitution s) {
                substitutions.put(s.position(), s.newResidue());
            } else if (edit instanceof SequenceEdit.Deletion d) {
                for (int p = d.start(); p <= d.end(); p++) {
                    if (p >= 1 && p <= n) deleted[p] = true;
                }
            } else if (edit instanceof SequenceEdit.Insertion in) {
                insertions.computeIfAbsent(in.afterPosition(), k -> new StringBuilder())
                          .append(in.residues());
            }
        }

        StringBuilder out = new StringBuilder(n);
        appendInsertion(out, insertions, 0);                               // insertions before residue 1
        for (int p = 1; p <= n; p++) {
            if (!deleted[p]) {
                out.append(substitutions.getOrDefault(p, original.get(p - 1)));
            }
            appendInsertion(out, insertions, p);                           // insertions after residue p
        }

        return new Sequence(original.getHeader(), out.toString());
    }

    private static void appendInsertion(StringBuilder out, Map<Integer, StringBuilder> insertions, int slot) {
        StringBuilder ins = insertions.get(slot);
        if (ins != null) out.append(ins);
    }
}
