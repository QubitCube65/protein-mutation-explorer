package org.example.project.model.ai;

import org.example.project.model.Sequence;

/**
 * Builds the prompts sent to the OpenAI proxy. Kept in the model layer next to
 * {@link EditParser} so the requested JSON schema and the parser stay in lockstep.
 *
 * <p>The system prompt pins down the exact response schema and numbering rules;
 * the user prompt supplies the current sequence and the natural-language request.
 */
public final class EditPromptBuilder {

    private EditPromptBuilder() {}

    public static String systemPrompt() {
        return """
            You are a protein-sequence editing assistant. The user gives you a protein
            sequence (one-letter amino-acid codes) and a natural-language editing request.
            Translate the request into a precise list of edits.

            Respond with a SINGLE JSON object only - no prose, no markdown, no code fences.
            Schema:
            {
              "edits": [ <edit>, ... ],
              "explanation": "one or two short sentences"
            }

            Each <edit> is exactly one of:
              {"type":"substitution","position":<int>,"newResidue":"<X>","originalResidue":"<X>"}
              {"type":"insertion","afterPosition":<int>,"residues":"<XX...>"}
              {"type":"deletion","start":<int>,"end":<int>}

            Rules:
            - The user message lists EVERY residue as "position:letter" (1-based). Use
              exactly those positions - never count or infer positions yourself. When a
              request targets residues by identity (e.g. "all cysteines"), scan the
              numbered listing and emit one edit for EACH matching position.
            - All positions are 1-based and relative to the sequence EXACTLY as given.
            - Every position refers to the original given sequence; do NOT renumber
              positions between successive edits.
            - Substitution: "position" is the residue to replace; always include
              "originalResidue" = the current letter at that position.
            - Insertion: "afterPosition" 0 means before residue 1; a value equal to the
              sequence length means append at the end.
            - Deletion: the range [start, end] is inclusive.
            - Use uppercase one-letter codes for the 20 standard amino acids only.
            - Produce ONLY edits that fulfil the request; make no unrelated changes.
            - For qualitative requests (e.g. "more hydrophilic", "more hydrophobic"),
              choose suitable standard-amino-acid substitutions and list each explicitly.
            - If the request cannot be satisfied, return an empty "edits" array and say
              why in "explanation".
            """;
    }

    public static String userPrompt(Sequence sequence, String request) {
        return "Current protein sequence (length " + sequence.length()
            + "). Every residue is given as position:letter, 1-based - use these exact positions:\n"
            + numbered(sequence) + "\n\n"
            + "Plain sequence (for reference only): " + sequence.toSequenceString() + "\n\n"
            + "Request: " + request.trim();
    }

    /**
     * Lists the sequence as {@code position:letter} pairs, ten per line, so the
     * model never has to count residues itself - the main cause of wrong positions
     * (especially further into the sequence).
     */
    private static String numbered(Sequence sequence) {
        StringBuilder sb = new StringBuilder(sequence.length() * 6);
        for (int i = 0; i < sequence.length(); i++) {
            sb.append(i + 1).append(':').append(sequence.get(i));
            sb.append((i + 1) % 10 == 0 ? '\n' : ' ');
        }
        return sb.toString().stripTrailing();
    }
}
