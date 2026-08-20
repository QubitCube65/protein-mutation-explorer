package org.example.project.model.ai;

/**
 * A single edit proposed by the AI, with all positions <strong>1-based and
 * relative to the current sequence</strong> (as mandated by the assignment).
 *
 * <p>Modelled as a sealed interface with one record per {@link EditType} so that
 * each variant carries exactly the fields it needs and nothing more. Instances
 * are immutable data holders - they describe a <em>proposed</em> change and never
 * modify a sequence themselves. Validation ({@link EditValidator}) and
 * application happen elsewhere, only after the user has approved the proposal.
 */
public sealed interface SequenceEdit
        permits SequenceEdit.Substitution, SequenceEdit.Insertion, SequenceEdit.Deletion {

    /** @return the kind of edit, for grouping and display. */
    EditType type();

    /** @return a short, human-readable one-line description for the preview table. */
    String describe();

    /**
     * Replace the residue at {@code position} with {@code newResidue}.
     *
     * @param position         1-based index of the residue to replace
     * @param newResidue       the new one-letter amino acid code (uppercase)
     * @param originalResidue  what the AI believes is currently at {@code position},
     *                         or {@code null} if it did not say. When present, the
     *                         validator checks it against the real residue as a
     *                         guard against hallucinated positions.
     */
    record Substitution(int position, char newResidue, Character originalResidue)
            implements SequenceEdit {

        @Override public EditType type() { return EditType.SUBSTITUTION; }

        @Override public String describe() {
            String from = originalResidue != null ? String.valueOf(originalResidue) : "?";
            return "Substitute position " + position + "  (" + from + " → " + newResidue + ")";
        }
    }

    /**
     * Insert {@code residues} after {@code afterPosition}.
     *
     * @param afterPosition 1-based position to insert after; {@code 0} means insert
     *                      before residue 1, and {@code length} means append at the end
     * @param residues      one or more one-letter amino acid codes (uppercase)
     */
    record Insertion(int afterPosition, String residues) implements SequenceEdit {

        @Override public EditType type() { return EditType.INSERTION; }

        @Override public String describe() {
            return "Insert \"" + residues + "\" after position " + afterPosition;
        }
    }

    /**
     * Delete the inclusive residue range {@code [start, end]}.
     *
     * @param start 1-based index of the first residue to delete
     * @param end   1-based index of the last residue to delete (inclusive)
     */
    record Deletion(int start, int end) implements SequenceEdit {

        @Override public EditType type() { return EditType.DELETION; }

        @Override public String describe() {
            return start == end
                ? "Delete position " + start
                : "Delete positions " + start + "–" + end;
        }
    }
}
