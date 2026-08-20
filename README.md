# Protein Mutation Explorer

A JavaFX desktop application for exploring how mutations affect protein structure.
Load a sequence, edit it — by hand or by describing the change in plain English —
predict the 3D fold, and compare the resulting conformations side by side.

Built as the semester project for *Advanced Java for Bioinformatics* (SoSe 2026,
Prof. Dr. Daniel Huson, Universität Tübingen).

## Run

```powershell
mvn javafx:run
```

Requires JDK 17+ and Maven; JavaFX 21 is pulled in as a dependency.

The natural-language editing feature needs an OpenAI proxy key — put it in a
git-ignored `openai-proxy.key` in the project root, or set `OPENAI_PROXY_KEY`.
Everything else works without it.

---

## What it does

**Edit sequences.** Load a FASTA file and every residue becomes a clickable tile.
Select by click, drag, or Ctrl+click, then mutate, insert, or delete. Everything is
undoable (Ctrl+Z / Ctrl+Y), including adding and removing conformations.

**Predict structures.** Sequences are folded with ESMFold and rendered in a JavaFX
`SubScene` as ball-and-stick (CPK colours, computed covalent bonds) or backbone
ribbon — drag to rotate, scroll to zoom, sliders for radii. Results are cached, so
re-folding a known sequence is instant. ESMFold's public API caps out at 400
residues; beyond that the app offers the precomputed AlphaFold DB structure,
looked up by the UniProt accession in the FASTA header.

**Edit by description.** Instead of clicking residues, describe the change:
*"mutate all cysteines to serines"*, *"insert a Gly-Ser linker after residue 80"*.
The model only ever *proposes* a list of edits — never touches the sequence. Each
proposal is parsed, validated against the real sequence, shown in a ✓/✗ preview
table, and applied only after you approve it. A stated original residue that
doesn't match the actual one is rejected outright, which catches hallucinated
positions. The full pipeline is documented in [`AI_VALIDATION.md`](AI_VALIDATION.md).

**Find and align relatives.** "Find Related" runs an NCBI BLAST search against
swissprot and feeds the hits straight into EBI Clustal Omega. The alignment appears
as a scrollable grid: differences from the query in orange, gaps muted, and columns
where ≥ 95 % of sequences agree tinted green. Optionally colour every residue by
physicochemical group.

**Keep everything in sync.** Selecting a residue in the sequence editor highlights
its Cα atom in the 3D viewer *and* the matching alignment column — and the same in
reverse, from any of the three. Gap columns are handled correctly. A statistics
panel charts amino acid composition for whichever sequence you click.

**Compare conformations.** Save any state as a conformation card. Cards hold
immutable snapshots, so clicking between them switches sequence and structure
together — flip back and forth to compare a mutant against the wild type. Sequences
can also be folded directly out of the alignment into a new card.

---

## Architecture

Strict MVP. Every sub-view is a self-contained package with its own FXML,
Controller (view) and Presenter (logic); `WindowView` is the single composition root
that builds the shared models and wires the presenters together. Presenters never
reference each other — they communicate through shared observable models
(`ConformationStore`, `ResidueSelectionModel`, `UndoManager`) and injected callbacks.

| Package | Responsibility |
|---|---|
| `window/` | Menu bar, tab layout, help dialog, composition root |
| `sequenceeditor/` | FASTA I/O, residue strip, editing, ESMFold / AlphaFold, BLAST trigger |
| `protein3d/` | 3D SubScene: ball-and-stick, ribbon, selection highlight |
| `conformationpanel/` | Conformation cards in a `FlowPane` |
| `alignment/` | MSA grid, row/column selection, conservation + residue colouring |
| `statistics/` | Composition bar chart and colour legend |
| `nledit/` | Modal AI editing dialog: propose → validate → preview → accept |
| `model/` | Domain types and service layer (see below) |
| `model/ai/` | Prompt building, transport, parsing, validation, safe application |

`model/` holds the domain types (`Sequence`, `Conformation`, `ProteinStructure`) and
one class per external concern — REST clients for ESMFold, AlphaFold, BLAST and
Clustal Omega, a PDB parser, bond calculation, and the CPK / Van-der-Waals / amino
acid colour lookups. All network calls run on daemon background threads with
progress in the status bar; the UI never blocks.

FXML lives under `src/main/resources/project/{package}/`.

**Layout:** two tabs under a persistent sequence toolbar — *Structure* (3D viewer +
conformation cards) and *Alignment* (MSA grid + statistics).

---

## External services

| Service | Purpose |
|---|---|
| [ESMFold](https://esmatlas.com) | Structure prediction (≤ 400 residues) |
| [AlphaFold DB](https://alphafold.ebi.ac.uk) | Precomputed structures for known proteins, any length |
| [NCBI BLAST](https://blast.ncbi.nlm.nih.gov) | Homology search against swissprot |
| [EBI Clustal Omega](https://www.ebi.ac.uk/jdispatcher/msa/clustalo) | Multiple sequence alignment |
| OpenAI proxy | Natural language → structured edit list |

A few implementation notes: BLAST's polling loop uses the RTOE estimate the server
returns to calibrate its first wait instead of hammering the endpoint. ESMFold is a
free community service and retries automatically on 504/503. Applying a mutation
clears the current structure — the "Run ESMFold" button glows blue whenever the
displayed fold is out of date.

---

## Provenance

Core implementation and design by me. AI assistance was used for structural
suggestions, debugging, CSS styling and API integration — all reviewed and adapted.
