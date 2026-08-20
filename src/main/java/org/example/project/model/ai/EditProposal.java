package org.example.project.model.ai;

import java.util.List;

/**
 * The parsed result of one AI request: the list of proposed {@link SequenceEdit}s
 * plus the model's short natural-language explanation. Purely a data holder - the
 * edits still have to be validated and user-approved before anything is applied.
 *
 * @param edits       the proposed edits, in the order returned by the model
 * @param explanation the model's short justification (may be empty)
 */
public record EditProposal(List<SequenceEdit> edits, String explanation) {

    public boolean isEmpty() {
        return edits == null || edits.isEmpty();
    }
}
