package org.example.project.model.ai;

import org.example.project.model.Sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the edits proposed by the AI before <em>anything</em> is applied.
 * This is the safety layer required by the assignment: "The AI system must not
 * directly modify the sequence. All proposed changes must be validated by the
 * program and approved by the user before application."
 *
 * <p>Each edit is checked <strong>independently against the current sequence</strong>,
 * because the assignment specifies that all positions are 1-based and relative to
 * the current sequence. The validator never mutates anything; it returns one
 * {@link ValidationResult} per edit (order preserved) and the caller applies only
 * the valid ones.
 *
 * <h2>Validation rules</h2>
 * <b>Common</b>
 * <ul>
 *   <li>Any residue letter must be one of the 20 standard amino acids
 *       ({@code ACDEFGHIKLMNPQRSTVWY}). Non-standard letters and the stop
 *       codon {@code '*'} are rejected - a fold target must be a real protein.</li>
 * </ul>
 * <b>Substitution</b>
 * <ul>
 *   <li>{@code position} must lie within {@code 1..length}.</li>
 *   <li>{@code newResidue} must be a standard amino acid.</li>
 *   <li>If the model stated an {@code originalResidue}, it must match the residue
 *       actually present at that position. A mismatch means the model was working
 *       from a wrong view of the sequence (a hallucinated position), so the edit
 *       is rejected.</li>
 *   <li>A substitution to the residue already present is accepted but flagged as
 *       a no-op.</li>
 * </ul>
 * <b>Insertion</b>
 * <ul>
 *   <li>{@code afterPosition} must lie within {@code 0..length} ({@code 0} =
 *       before residue 1, {@code length} = append at the end).</li>
 *   <li>{@code residues} must be non-empty and every letter a standard amino acid.</li>
 * </ul>
 * <b>Deletion</b>
 * <ul>
 *   <li>{@code start} and {@code end} must lie within {@code 1..length} and
 *       {@code start <= end}.</li>
 *   <li>Deleting the entire sequence is accepted but flagged, since the result
 *       cannot be folded.</li>
 * </ul>
 */
public final class EditValidator {

    /** The 20 standard amino acids, one-letter codes. */
    private static final String STANDARD_AA = "ACDEFGHIKLMNPQRSTVWY";

    private EditValidator() {}

    /**
     * Validates every edit against {@code sequence}.
     *
     * @return one result per edit, in the same order; never {@code null}
     */
    public static List<ValidationResult> validate(Sequence sequence, List<SequenceEdit> edits) {
        List<ValidationResult> results = new ArrayList<>();
        int len = sequence.length();
        for (SequenceEdit edit : edits) {
            results.add(validateOne(sequence, len, edit));
        }
        return results;
    }

    /** @return {@code true} if at least one result is valid. */
    public static boolean hasAnyValid(List<ValidationResult> results) {
        return results.stream().anyMatch(ValidationResult::valid);
    }

    // ── Per-edit validation ────────────────────────────────────────────────

    private static ValidationResult validateOne(Sequence seq, int len, SequenceEdit edit) {
        if (edit instanceof SequenceEdit.Substitution s) {
            return validateSubstitution(seq, len, s);
        } else if (edit instanceof SequenceEdit.Insertion i) {
            return validateInsertion(len, i);
        } else if (edit instanceof SequenceEdit.Deletion d) {
            return validateDeletion(len, d);
        }
        return ValidationResult.rejected(edit, "Unknown edit type.");
    }

    private static ValidationResult validateSubstitution(Sequence seq, int len,
                                                         SequenceEdit.Substitution s) {
        if (s.position() < 1 || s.position() > len) {
            return ValidationResult.rejected(s,
                "Position " + s.position() + " is out of range (1.." + len + ").");
        }
        if (!isStandard(s.newResidue())) {
            return ValidationResult.rejected(s,
                "'" + s.newResidue() + "' is not a standard amino acid.");
        }
        char current = seq.get(s.position() - 1);
        if (s.originalResidue() != null && Character.toUpperCase(s.originalResidue()) != current) {
            return ValidationResult.rejected(s,
                "Model expected '" + s.originalResidue() + "' at position " + s.position()
                + " but sequence has '" + current + "' (position mismatch).");
        }
        if (current == s.newResidue()) {
            return ValidationResult.ok(s, "No change (already '" + current + "').");
        }
        return ValidationResult.ok(s);
    }

    private static ValidationResult validateInsertion(int len, SequenceEdit.Insertion i) {
        if (i.afterPosition() < 0 || i.afterPosition() > len) {
            return ValidationResult.rejected(i,
                "Insert position " + i.afterPosition() + " is out of range (0.." + len + ").");
        }
        if (i.residues() == null || i.residues().isEmpty()) {
            return ValidationResult.rejected(i, "No residues to insert.");
        }
        for (char c : i.residues().toCharArray()) {
            if (!isStandard(c)) {
                return ValidationResult.rejected(i,
                    "'" + c + "' is not a standard amino acid.");
            }
        }
        return ValidationResult.ok(i);
    }

    private static ValidationResult validateDeletion(int len, SequenceEdit.Deletion d) {
        if (d.start() < 1 || d.start() > len) {
            return ValidationResult.rejected(d,
                "Start " + d.start() + " is out of range (1.." + len + ").");
        }
        if (d.end() < 1 || d.end() > len) {
            return ValidationResult.rejected(d,
                "End " + d.end() + " is out of range (1.." + len + ").");
        }
        if (d.start() > d.end()) {
            return ValidationResult.rejected(d,
                "Start " + d.start() + " is after end " + d.end() + ".");
        }
        if (d.start() == 1 && d.end() == len) {
            return ValidationResult.ok(d, "Deletes the entire sequence - nothing left to fold.");
        }
        return ValidationResult.ok(d);
    }

    private static boolean isStandard(char c) {
        return STANDARD_AA.indexOf(Character.toUpperCase(c)) >= 0;
    }
}
