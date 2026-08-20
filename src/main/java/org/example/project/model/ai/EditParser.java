package org.example.project.model.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the raw JSON string returned by {@link OpenAIService} into a structured
 * {@link EditProposal}. Structural problems (not JSON, missing {@code "edits"}
 * array, an edit missing required fields, an unknown edit type) raise an
 * {@link EditParseException}. Semantic checks (positions in range, valid amino
 * acids, …) are <em>not</em> done here - that is {@link EditValidator}'s job.
 *
 * <p>The expected schema (see the system prompt) is:
 * <pre>
 * {
 *   "edits": [
 *     {"type":"substitution","position":57,"newResidue":"A","originalResidue":"K"},
 *     {"type":"insertion","afterPosition":80,"residues":"GS"},
 *     {"type":"deletion","start":40,"end":42}
 *   ],
 *   "explanation": "…"
 * }
 * </pre>
 * A few common field-name synonyms are accepted defensively so a slightly
 * off-schema response still parses.
 */
public final class EditParser {

    private EditParser() {}

    public static EditProposal parse(String json) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new EditParseException("AI response was not a JSON object: " + e.getMessage(), e);
        }

        if (!root.has("edits") || !root.get("edits").isJsonArray()) {
            throw new EditParseException("AI response is missing an \"edits\" array.");
        }

        List<SequenceEdit> edits = new ArrayList<>();
        JsonArray array = root.getAsJsonArray("edits");
        for (int i = 0; i < array.size(); i++) {
            JsonElement el = array.get(i);
            if (!el.isJsonObject()) {
                throw new EditParseException("Edit #" + (i + 1) + " is not a JSON object.");
            }
            edits.add(parseEdit(el.getAsJsonObject(), i + 1));
        }

        String explanation = optString(root, "");
        return new EditProposal(edits, explanation);
    }

    // ── Per-edit parsing ───────────────────────────────────────────────────

    private static SequenceEdit parseEdit(JsonObject o, int oneBasedIndex) {
        String type = requireString(o, oneBasedIndex, "type").trim().toLowerCase();
        switch (type) {
            case "substitution", "substitute", "mutation" -> {
                int position = requireInt(o, oneBasedIndex, "position", "index", "pos");
                char newResidue = requireResidue(o, oneBasedIndex, "newResidue", "residue", "to", "new");
                Character original = optResidue(o, "originalResidue", "original", "from");
                return new SequenceEdit.Substitution(position, newResidue, original);
            }
            case "insertion", "insert" -> {
                int after = requireInt(o, oneBasedIndex, "afterPosition", "position", "after", "pos");
                String residues = requireResidues(o, oneBasedIndex, "residues", "sequence", "residue");
                return new SequenceEdit.Insertion(after, residues);
            }
            case "deletion", "delete" -> {
                int start = requireInt(o, oneBasedIndex, "start", "from", "position", "pos");
                int end = optInt(o, start, "end", "to");
                return new SequenceEdit.Deletion(start, end);
            }
            default -> throw new EditParseException(
                "Edit #" + oneBasedIndex + " has unknown type \"" + type + "\".");
        }
    }

    // ── Field helpers ──────────────────────────────────────────────────────

    private static String firstPresent(JsonObject o, String... names) {
        for (String n : names) {
            if (o.has(n) && !o.get(n).isJsonNull()) return n;
        }
        return null;
    }

    private static int requireInt(JsonObject o, int idx, String... names) {
        String name = firstPresent(o, names);
        if (name == null) {
            throw new EditParseException(
                "Edit #" + idx + " is missing an integer field (" + String.join("/", names) + ").");
        }
        try {
            return o.get(name).getAsInt();
        } catch (RuntimeException e) {
            throw new EditParseException(
                "Edit #" + idx + " field \"" + name + "\" is not an integer: " + o.get(name));
        }
    }

    private static int optInt(JsonObject o, int fallback, String... names) {
        String name = firstPresent(o, names);
        if (name == null) return fallback;
        try {
            return o.get(name).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String requireString(JsonObject o, int idx, String... names) {
        String name = firstPresent(o, names);
        if (name == null) {
            throw new EditParseException(
                "Edit #" + idx + " is missing field \"" + String.join("/", names) + "\".");
        }
        return o.get(name).getAsString();
    }

    private static String optString(JsonObject o, String fallback) {
        String name = firstPresent(o, "explanation", "reason", "note");
        return name == null ? fallback : o.get(name).getAsString();
    }

    /** Reads a required single amino-acid letter (uppercased). */
    private static char requireResidue(JsonObject o, int idx, String... names) {
        String s = requireString(o, idx, names).trim().toUpperCase();
        if (s.length() != 1) {
            throw new EditParseException(
                "Edit #" + idx + " expected a single-letter residue but got \"" + s + "\".");
        }
        return s.charAt(0);
    }

    /** Reads an optional single amino-acid letter (uppercased), or {@code null}. */
    private static Character optResidue(JsonObject o, String... names) {
        String name = firstPresent(o, names);
        if (name == null) return null;
        String s = o.get(name).getAsString().trim().toUpperCase();
        return s.length() == 1 ? s.charAt(0) : null;
    }

    /** Reads a required non-empty residue string (uppercased). */
    private static String requireResidues(JsonObject o, int idx, String... names) {
        String s = requireString(o, idx, names).trim().toUpperCase();
        if (s.isEmpty()) {
            throw new EditParseException("Edit #" + idx + " has empty residues.");
        }
        return s;
    }
}
