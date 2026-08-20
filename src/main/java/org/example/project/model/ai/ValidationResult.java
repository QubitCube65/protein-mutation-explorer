package org.example.project.model.ai;

/**
 * The outcome of validating a single {@link SequenceEdit} against the current
 * sequence. Shown to the user in the preview table; only results with
 * {@code valid == true} are ever applied.
 *
 * @param edit    the edit that was checked
 * @param valid   {@code true} if the edit passed every validation rule
 * @param message a short human-readable note: why it was rejected, or an
 *                informational remark for an accepted edit (may be empty)
 */
public record ValidationResult(SequenceEdit edit, boolean valid, String message) {

    public static ValidationResult ok(SequenceEdit edit) {
        return new ValidationResult(edit, true, "");
    }

    public static ValidationResult ok(SequenceEdit edit, String note) {
        return new ValidationResult(edit, true, note);
    }

    public static ValidationResult rejected(SequenceEdit edit, String reason) {
        return new ValidationResult(edit, false, reason);
    }

    /** @return the one-line description of the underlying edit. */
    public String editDescription() {
        return edit.describe();
    }
}
