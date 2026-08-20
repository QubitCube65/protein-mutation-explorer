package org.example.project.model.ai;

/**
 * Thrown when the AI response cannot be parsed into a well-formed
 * {@link EditProposal} (e.g. it is not JSON, the {@code "edits"} array is
 * missing, or an edit object lacks required fields). Signals a
 * <em>structural</em> problem with the response - distinct from an edit that
 * parses fine but is semantically invalid, which is reported by
 * {@link EditValidator} instead.
 */
public class EditParseException extends RuntimeException {

    public EditParseException(String message) {
        super(message);
    }

    public EditParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
