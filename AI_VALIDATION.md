# How AI-Generated Modifications Are Validated

The Protein Mutation Explorer lets you edit a protein sequence by describing the
change in plain language. This document describes how the program keeps that
feature safe — how a language model's output is prevented from reaching the
sequence unchecked.

## Core guarantee

> **The AI system never modifies the sequence.**
> It only *proposes* a list of edits. Every proposed edit is validated by the
> program, previewed, and explicitly approved by the user before anything is
> applied. Nothing the model returns can reach the sequence unchecked.

The model's output is treated as **untrusted data**, not as commands.

## The pipeline

```
 user request + current sequence
        │
        ▼
 EditPromptBuilder ──► OpenAIService ──► raw JSON string   (transport only)
        │
        ▼
 EditParser ──► EditProposal (List<SequenceEdit>)          (STRUCTURAL check)
        │
        ▼
 EditValidator ──► List<ValidationResult>  (✓ / ✗)         (SEMANTIC check)
        │
        ▼
 Preview table in NlEdit dialog  ──►  user clicks "Apply"  (HUMAN approval)
        │
        ▼
 EditApplier (valid + approved edits only) ──► new Sequence
        │
        ▼
 ESMFold ──► new Conformation stored in history
```

Each stage is a separate class, so the safety layers are easy to inspect and test.

## Two kinds of validation

### 1. Structural validation - `EditParser`

Rejects a response that is not shaped like a valid proposal, raising
`EditParseException`:

- the response is not a JSON object;
- the `"edits"` array is missing or is not an array;
- an edit object is missing a required field (`type`, `position`, `residues`, …);
- an edit has an unknown `type`;
- a residue field is not a single valid letter where one is expected.

### 2. Semantic validation - `EditValidator`

Each parsed edit is checked **independently against the current sequence**
(positions are 1-based and relative to the current sequence, as required). The
validator never mutates anything; it returns one `ValidationResult` (valid /
rejected + reason) per edit, and **only edits marked valid are ever applied**.

**Common**
- Every residue letter must be one of the 20 standard amino acids
  (`ACDEFGHIKLMNPQRSTVWY`). Non-standard letters and the stop codon `*` are
  rejected - a fold target must be a real protein.

**Substitution** `{type, position, newResidue, originalResidue?}`
- `position` must lie within `1..length`.
- `newResidue` must be a standard amino acid.
- If the model stated an `originalResidue`, it must match the residue actually
  present at that position. A mismatch means the model was working from a wrong
  view of the sequence (a hallucinated position) → **rejected**.
- A substitution to the residue already present is accepted but flagged as a no-op.

**Insertion** `{type, afterPosition, residues}`
- `afterPosition` must lie within `0..length`
  (`0` = before residue 1, `length` = append at the end).
- `residues` must be non-empty and every letter a standard amino acid.

**Deletion** `{type, start, end}`
- `start` and `end` must lie within `1..length` and `start <= end`.
- Deleting the entire sequence is accepted but flagged (nothing left to fold).

## Human approval

The `NlEdit` dialog shows every proposed edit in a preview table marked **✓ valid**
(green) or **✗ rejected** (red) with the reason, plus the model's short
explanation. The **"Apply accepted edits"** button is enabled only when at least
one edit is valid, and it applies **only the valid edits**. **"Cancel"** discards
the whole proposal.

## Safe application

`EditApplier` applies the approved edits by rebuilding a **new** `Sequence` in a
single pass over the original residues, interpreting all positions against the
original coordinates. This avoids the classic bug where applying one edit shifts
the positions of the following edits. The whole batch is a single undo step.

After application the edited sequence is folded with ESMFold and the resulting
conformation is stored in the conformation history.

## Where to look in the code

| Concern | Class |
| --- | --- |
| Build the prompt / schema | `model/ai/EditPromptBuilder` |
| Transport to the proxy | `model/ai/OpenAIService` |
| API key (never committed) | `model/ai/ProxyKeyProvider` |
| Parse response (structural) | `model/ai/EditParser`, `EditParseException` |
| Edit data model | `model/ai/SequenceEdit`, `EditType`, `EditProposal` |
| **Validate (semantic)** | `model/ai/EditValidator`, `ValidationResult` |
| Apply safely | `model/ai/EditApplier` |
| Preview + approval UI | `nledit/NlEdit.fxml`, `NlEditController`, `NlEditPresenter`, `NlEditView` |
