package org.example.project.sequenceeditor;

import javafx.application.Platform;
import javafx.collections.SetChangeListener;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.project.alignment.AlignmentPresenter;
import org.example.project.model.*;
import org.example.project.model.ai.EditApplier;
import org.example.project.model.ai.SequenceEdit;
import org.example.project.nledit.NlEditView;
import org.example.project.protein3d.Protein3DPresenter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;

/**
 * Manages the sequence editor:
 *  - loading / saving FASTA files
 *  - displaying residues as clickable buttons with drag-range selection
 *  - applying mutations (single or multi-residue), insert, and delete with undo/redo
 *  - submitting the sequence to ESMFold (with automatic retry on 504/timeout) and showing in 3D
 *  - adding conformations to the shared ConformationStore
 */
public final class SequenceEditorPresenter {

    // All 20 standard amino acids plus stop codon
    private static final char[] AMINO_ACIDS = "ACDEFGHIKLMNPQRSTVWY".toCharArray();

    private static final String CLS_DEFAULT  = "residue-btn";
    private static final String CLS_SELECTED = "residue-btn-selected";
    private static final int    ESMFOLD_MAX_LENGTH = 400;
    private static final int    ESMFOLD_MAX_RETRIES = 3;

    private final SequenceEditorController controller;
    private final ConformationStore conformationStore;
    private final ResidueSelectionModel selectionModel;
    private final UndoManager undoManager;
    private final Protein3DPresenter protein3DPresenter;
    private final AlignmentPresenter alignmentPresenter;

    private final ESMFoldService esmFoldService = new ESMFoldService();
    private final AlphaFoldService alphaFoldService = new AlphaFoldService();
    private final ESMFoldCache esmFoldCache = new ESMFoldCache();
    private final BlastService blastService = new BlastService();
    private final PDBParser pdbParser = new PDBParser();
    private final List<Button> residueButtons = new ArrayList<>();
    private final List<BlastHit> lastBlastResults = new ArrayList<>();
    private ProgressIndicator foldingSpinner;


    private Sequence currentSequence;
    private ProteinStructure latestStructure;

    // Drag-select state
    private int dragStartIdx = -1;

    // Controls that live in other panels, injected via attachExternalControls()
    private Button aiEditButton;
    private Button addConformationButton;
    private TextField descriptionField;

    // Whether the sequence was edited since it was last loaded/restored (for the
    // "can't fold an edited long sequence" hard stop) and the Run-ESMFold tooltip.
    private boolean sequenceEdited = false;
    private final Tooltip esmFoldTooltip = new Tooltip();

    public SequenceEditorPresenter(SequenceEditorController controller,
                                   ConformationStore conformationStore,
                                   ResidueSelectionModel selectionModel,
                                   UndoManager undoManager,
                                   Protein3DPresenter protein3DPresenter,
                                   AlignmentPresenter alignmentPresenter) {
        this.controller         = controller;
        this.conformationStore  = conformationStore;
        this.selectionModel     = selectionModel;
        this.undoManager        = undoManager;
        this.protein3DPresenter = protein3DPresenter;
        this.alignmentPresenter = alignmentPresenter;

        // Fill mutation ComboBox with all standard amino acids plus stop codon
        for (char aa : AMINO_ACIDS) controller.getMutationComboBox().getItems().add(aa);
        controller.getMutationComboBox().getItems().add('*');

        // BLAST hits dropdown
        controller.getBlastHitsComboBox().getItems().addAll(5, 10, 25, 50);
        controller.getBlastHitsComboBox().setValue(10);

        controller.getLoadFastaButton()      .setOnAction(e -> onLoadFasta());
        controller.getSaveFastaButton()      .setOnAction(e -> onSaveFasta());
        controller.getApplyMutationButton()  .setOnAction(e -> onApplyMutation());
        controller.getInsertButton()         .setOnAction(e -> onInsertResidue());
        controller.getDeleteButton()         .setOnAction(e -> onDeleteSelected());
        controller.getEsmFoldButton()        .setOnAction(e -> onEsmFold());
        controller.getEsmFoldButton()        .setTooltip(esmFoldTooltip);
        controller.getFindRelatedButton()   .setOnAction(e -> onFindRelated());
        controller.getUndoButton()           .setOnAction(e -> undoManager.undo());
        controller.getRedoButton()           .setOnAction(e -> undoManager.redo());
        controller.getUndoButton().disableProperty().bind(undoManager.canUndoProperty().not());
        controller.getRedoButton().disableProperty().bind(undoManager.canRedoProperty().not());

        // React to selection changes
        selectionModel.selectedIndicesProperty().addListener(
            (SetChangeListener<Integer>) change -> {
                updateHighlights();
                syncButtons();
            });

        controller.getMutationComboBox().valueProperty().addListener((obs, o, n) -> syncButtons());
    }

    /**
     * Injects controls that live in other panels: the "AI Edit…" button now sits in
     * the 3D viewer, and the description field + "Add" button in the conformation
     * panel. Wiring them here keeps all editor behaviour in this presenter while the
     * buttons are placed where they make the most sense in the UI. Called once by the
     * composition root ({@code WindowView}) right after construction.
     */
    public void attachExternalControls(Button aiEditButton,
                                       Button addConformationButton,
                                       TextField descriptionField) {
        this.aiEditButton          = aiEditButton;
        this.addConformationButton = addConformationButton;
        this.descriptionField      = descriptionField;
        aiEditButton.setOnAction(e -> onAiEdit());
        addConformationButton.setOnAction(e -> onAddConformation());
    }

    /** Highlights the "Run ESMFold" button (blue glow) while the shown 3D structure is out of date. */
    private void setFoldReady(boolean ready) {
        var styles = controller.getEsmFoldButton().getStyleClass();
        if (ready) {
            if (!styles.contains("fold-ready")) styles.add("fold-ready");
        } else {
            styles.remove("fold-ready");
        }
    }

    /**
     * Updates the Run-ESMFold button's tooltip and its "blocked" look based on the
     * current sequence. A long sequence that has been edited cannot be folded
     * (ESMFold's limit is {@code ESMFOLD_MAX_LENGTH}, and AlphaFold DB only holds the
     * original), so the button is shown as blocked and its click is a hard stop.
     */
    private void refreshEsmFoldState() {
        Button btn = controller.getEsmFoldButton();
        btn.getStyleClass().remove("fold-blocked");
        if (currentSequence == null) {
            esmFoldTooltip.setText("Load a sequence first.");
            return;
        }
        int len = currentSequence.toFoldableSequenceString().length();
        boolean tooLong = len > ESMFOLD_MAX_LENGTH;

        if (tooLong && sequenceEdited) {
            setFoldReady(false);                       // no inviting glow while blocked
            btn.getStyleClass().add("fold-blocked");
            esmFoldTooltip.setText(
                "This edited sequence has " + len + " residues (ESMFold limit: " + ESMFOLD_MAX_LENGTH + ").\n"
                + "Edited long sequences can't be folded, and AlphaFold DB only has the original.\n"
                + "Reload the original protein, or shorten it to " + ESMFOLD_MAX_LENGTH
                + " residues or fewer to fold your edits.");
        } else if (tooLong) {
            esmFoldTooltip.setText(
                "Too long for ESMFold (" + len + " residues) - click to load the original "
                + "AlphaFold DB structure.");
        } else {
            esmFoldTooltip.setText("Fold the current sequence with ESMFold.");
        }
    }

    /** Hard stop when the user tries to fold an edited sequence that is over the ESMFold limit. */
    private void showLongEditedStop(int length) {
        showError("Cannot fold this sequence",
            "You edited a sequence with " + length + " residues, above the ESMFold limit of "
            + ESMFOLD_MAX_LENGTH + ".\n\n"
            + "ESMFold cannot fold it, and the AlphaFold database only has the ORIGINAL protein - "
            + "your edits would not be reflected, so no structure is loaded.\n\n"
            + "To proceed:\n"
            + "• Reload the original protein to view its AlphaFold structure, or\n"
            + "• Shorten the sequence to " + ESMFOLD_MAX_LENGTH
            + " residues or fewer to fold your edits with ESMFold.");
    }

    // ── Conformation restore ───────────────────────────────────────────────

    /**
     * Restores a saved conformation: loads its sequence into the editor and
     * shows its 3D structure (or clears the viewer if no structure was saved).
     * Called from ConformationPanelPresenter when the user clicks a card.
     */
    public void loadConformation(Conformation conformation) {
        currentSequence = new Sequence(conformation.getDescription(), conformation.getSequence());
        latestStructure = conformation.getStructure();
        selectionModel.clearSelection();
        undoManager.clear();
        alignmentPresenter.clear();   // old alignment belongs to the previous sequence
        displaySequence();
        protein3DPresenter.showStructure(latestStructure);

        controller.getSaveFastaButton()      .setDisable(false);
        controller.getEsmFoldButton()        .setDisable(false);
        controller.getBlastHitsComboBox()    .setDisable(false);
        controller.getFindRelatedButton()    .setDisable(false);
        addConformationButton.setDisable(false);
        aiEditButton.setDisable(false);
        sequenceEdited = false;
        setFoldReady(latestStructure == null);
        refreshEsmFoldState();

        if (latestStructure != null) {
            setStatus("Loaded: " + conformation.getDescription()
                + " - " + currentSequence.length() + " aa · 3D structure restored");
        } else {
            setStatus("Loaded: " + conformation.getDescription()
                + " - " + currentSequence.length() + " aa · run ESMFold to fold");
        }
    }

    // ── FASTA Load / Save ──────────────────────────────────────────────────

    private void onLoadFasta() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open FASTA File");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("FASTA Files", "*.fasta", "*.fa", "*.faa"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = fc.showOpenDialog(controller.getLoadFastaButton().getScene().getWindow());
        if (file == null) return;

        try {
            currentSequence = FastaIO.loadFromFile(file);
            latestStructure = null;
            selectionModel.clearSelection();
            undoManager.clear();
            alignmentPresenter.clear();   // old alignment belongs to the previous sequence
            displaySequence();
            setStatus("Loaded: " + currentSequence.getHeader()
                      + "  (" + currentSequence.length() + " residues)");
            controller.getSaveFastaButton()      .setDisable(false);
            controller.getEsmFoldButton()        .setDisable(false);
            controller.getBlastHitsComboBox()    .setDisable(false);
            controller.getFindRelatedButton()    .setDisable(false);
            addConformationButton.setDisable(false);
            aiEditButton.setDisable(false);
            protein3DPresenter.showStructure(null);
            sequenceEdited = false;
            setFoldReady(true);
            refreshEsmFoldState();
        } catch (Exception ex) {
            showError("Load Error", ex.getMessage());
        }
    }

    private void onSaveFasta() {
        if (currentSequence == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Save FASTA File");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("FASTA Files", "*.fasta", "*.fa"));
        File file = fc.showSaveDialog(controller.getSaveFastaButton().getScene().getWindow());
        if (file == null) return;
        try {
            FastaIO.saveToFile(currentSequence, file);
            setStatus("Saved to " + file.getName());
        } catch (Exception ex) {
            showError("Save Error", ex.getMessage());
        }
    }

    // ── Mutation (replace) ─────────────────────────────────────────────────

    private static final int BULK_MUTATION_THRESHOLD = 10;

    private void onApplyMutation() {
        if (currentSequence == null || selectionModel.isEmpty()) return;
        Character newAA = controller.getMutationComboBox().getValue();
        if (newAA == null) return;

        List<Integer> indices = new ArrayList<>(selectionModel.selectedIndicesProperty());
        Collections.sort(indices);

        if (indices.size() >= BULK_MUTATION_THRESHOLD) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Bulk Mutation");
            confirm.setHeaderText("Mutate " + indices.size() + " residues?");
            confirm.setContentText("You are about to change " + indices.size()
                + " residues to " + newAA + ". This action can be undone.");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) return;
        }

        // Apply and record old values for undo
        Map<Integer, Character> oldValues = new LinkedHashMap<>();
        for (int idx : indices) {
            char old = currentSequence.mutate(idx, newAA);
            oldValues.put(idx, old);
            refreshButton(idx);
        }

        selectionModel.clearSelection();
        markStructureStale();
        if (indices.size() == 1) {
            setStaleStatus("Mutated pos " + (indices.get(0) + 1) + ": "
                + oldValues.get(indices.get(0)) + " → " + newAA);
        } else {
            setStaleStatus("Mutated " + indices.size() + " residues to " + newAA);
        }

        undoManager.push(new UndoManager.Edit() {
            public void undo() {
                oldValues.forEach((idx, old) -> { currentSequence.mutate(idx, old); refreshButton(idx); });
                markStructureStale();
                setStaleStatus("Undo: restored " + indices.size() + " residue(s)");
            }
            public void redo() {
                indices.forEach(idx -> { currentSequence.mutate(idx, newAA); refreshButton(idx); });
                markStructureStale();
                setStaleStatus("Redo: mutated " + indices.size() + " residue(s) to " + newAA);
            }
            public String label() { return indices.size() == 1 ? "Mutation" : "Mutate " + indices.size() + " residues"; }
        });
    }

    // ── Insert residue ─────────────────────────────────────────────────────

    private void onInsertResidue() {
        if (currentSequence == null) return;
        Character chosen = controller.getMutationComboBox().getValue();
        if (chosen == null) {
            showError("No Amino Acid Selected", "Please select an amino acid from the 'AA:' dropdown before inserting.");
            return;
        }

        // Insert after last selected position, or append if nothing selected
        int insertIdx = selectionModel.isEmpty()
            ? currentSequence.length()
            : selectionModel.getLastSelected() + 1;

        currentSequence.insert(insertIdx, chosen);
        displaySequence();
        selectionModel.setSingle(insertIdx);
        markStructureStale();
        setStaleStatus("Inserted '" + chosen + "' at position " + (insertIdx + 1));

        final int finalIdx = insertIdx;
        final char finalAA = chosen;
        undoManager.push(new UndoManager.Edit() {
            public void undo() { currentSequence.delete(finalIdx); displaySequence(); markStructureStale(); setStaleStatus("Undo: removed inserted '" + finalAA + "'"); }
            public void redo() { currentSequence.insert(finalIdx, finalAA); displaySequence(); selectionModel.setSingle(finalIdx); markStructureStale(); setStaleStatus("Redo: re-inserted '" + finalAA + "' at pos " + (finalIdx + 1)); }
            public String label() { return "Insert '" + finalAA + "'"; }
        });
    }

    // ── Delete selected residues ───────────────────────────────────────────

    private void onDeleteSelected() {
        if (currentSequence == null || selectionModel.isEmpty()) return;

        List<Integer> indices = new ArrayList<>(selectionModel.selectedIndicesProperty());
        Collections.sort(indices);

        // Delete from highest index down so earlier indices stay valid
        List<int[]> deleted = new ArrayList<>(); // each entry: [index, char]
        for (int i = indices.size() - 1; i >= 0; i--) {
            int idx = indices.get(i);
            char c = currentSequence.delete(idx);
            deleted.add(0, new int[]{idx, c}); // prepend to keep ascending order
        }

        selectionModel.clearSelection();
        displaySequence();
        markStructureStale();
        String deletedStr = deleted.stream().map(e -> String.valueOf((char) e[1])).collect(Collectors.joining());
        setStaleStatus("Deleted " + deleted.size() + " residue(s): " + deletedStr);

        undoManager.push(new UndoManager.Edit() {
            public void undo() {
                for (int[] e : deleted) currentSequence.insert(e[0], (char) e[1]);
                displaySequence();
                markStructureStale();
                setStaleStatus("Undo: restored " + deleted.size() + " residue(s)");
            }
            public void redo() {
                for (int i = deleted.size() - 1; i >= 0; i--)
                    currentSequence.delete(deleted.get(i)[0]);
                displaySequence();
                markStructureStale();
                setStaleStatus("Redo: deleted " + deleted.size() + " residue(s)");
            }
            public String label() { return "Delete " + deleted.size() + " residue(s)"; }
        });
    }

    /**
     * Marks the 3D structure as stale after a sequence edit.
     * The model stays visible so the user can keep using it as a reference;
     * it will only update when they press "Run ESMFold" again.
     */
    private void markStructureStale() {
        latestStructure = null;
        sequenceEdited = true;
        setFoldReady(true);   // remind the user to re-fold
        refreshEsmFoldState();
        // Do NOT clear the 3D viewer - show the "run ESMFold" hint instead
    }

    // ── ESMFold ────────────────────────────────────────────────────────────

    private void onEsmFold() {
        if (currentSequence == null) return;

        String seq = currentSequence.toFoldableSequenceString();
        boolean hadStop = currentSequence.containsStopCodon();

        if (seq.isEmpty()) {
            showError("Empty Sequence",
                "The sequence contains only stop codons ('*'). There is nothing to fold.");
            return;
        }

        if (seq.length() > ESMFOLD_MAX_LENGTH) {
            if (sequenceEdited) showLongEditedStop(seq.length());
            else                offerAlphaFold(seq.length());
            return;
        }

        // Check cache first
        ProteinStructure cached = esmFoldCache.get(seq);
        if (cached != null) {
            latestStructure = cached;
            protein3DPresenter.showStructure(latestStructure);
            setFoldReady(false);
            setStatus("Folded (cached): " + latestStructure.getResidueCount()
                + " residues, " + latestStructure.getAtomCount() + " atoms");
            System.out.println("[ESMFold] Cache hit for sequence of length " + seq.length());
            return;
        }

        controller.getEsmFoldButton().setDisable(true);
        showFoldingSpinner(true);
        String baseStatus = hadStop
            ? "Stop codon(s) stripped before folding (" + seq.length() + " residues) - submitting…"
            : "Submitting " + seq.length() + " residues to ESMFold… (may take 1–2 min)";
        setStatus(baseStatus);
        System.out.println("[ESMFold] Submitting sequence of length " + seq.length());

        Task<ProteinStructure> task = new Task<>() {
            @Override protected ProteinStructure call() throws Exception {
                for (int attempt = 1; attempt <= ESMFOLD_MAX_RETRIES; attempt++) {
                    if (attempt > 1) {
                        final int a = attempt;
                        Platform.runLater(() -> controller.getStatusLabel().setText(
                            "ESMFold: server overloaded - retry " + a + "/" + ESMFOLD_MAX_RETRIES
                            + " (waiting " + (a * 5) + " s)…"));
                        Thread.sleep(attempt * 5_000L);
                    }
                    try {
                        String pdbText = esmFoldService.fold(seq);
                        System.out.println("[ESMFold] Received PDB, parsing…");
                        return pdbParser.parseContent("esmfold", pdbText);
                    } catch (java.io.IOException e) {
                        String m = e.getMessage();
                        boolean retryable = m != null && (m.contains("504") || m.contains("503")
                            || m.contains("timed out") || m.contains("timeout")
                            || m.contains("SocketTimeout") || m.contains("connect"));
                        System.err.println("[ESMFold] Attempt " + attempt + " failed: " + m);
                        if (!retryable || attempt == ESMFOLD_MAX_RETRIES) throw e;
                    }
                }
                throw new java.io.IOException("unreachable");
            }
        };

        task.setOnSucceeded(evt -> {
            ProteinStructure result = task.getValue();
            controller.getEsmFoldButton().setDisable(false);
            showFoldingSpinner(false);
            if (result == null || result.getAtomCount() == 0) {
                setStatus("ESMFold returned an empty structure - server may be temporarily down.");
                showError("ESMFold Error",
                    "The server responded but returned no atoms.\n"
                    + "This usually means the server is under heavy load. Please try again in a few minutes.");
                return;
            }
            latestStructure = result;
            esmFoldCache.put(seq, result);
            protein3DPresenter.showStructure(latestStructure);
            setFoldReady(false);
            String warnings = pdbParser.getLastParseWarnings() > 0
                ? " (" + pdbParser.getLastParseWarnings() + " malformed lines skipped)"
                : "";
            setStatus("Folded: " + latestStructure.getResidueCount()
                + " residues, " + latestStructure.getAtomCount() + " atoms"
                + (hadStop ? " (stop codon(s) were excluded)" : "") + warnings);
            System.out.println("[ESMFold] Done: " + latestStructure);
        });

        task.setOnFailed(evt -> {
            controller.getEsmFoldButton().setDisable(false);
            showFoldingSpinner(false);
            Throwable ex = task.getException();
            String raw = ex != null ? ex.getMessage() : null;
            String msg;
            if (raw != null && (raw.contains("504") || raw.contains("timed out")
                    || raw.contains("timeout") || raw.contains("SocketTimeout"))) {
                msg = "ESMFold server timed out (HTTP 504) after " + ESMFOLD_MAX_RETRIES + " attempts.\n"
                    + "The service is best-effort and frequently overloaded.\n"
                    + "Please wait a few minutes and try again.";
            } else if (raw != null && raw.contains("503")) {
                msg = "ESMFold service is temporarily unavailable (HTTP 503).\n"
                    + "Please try again in a few minutes.";
            } else if (raw != null && raw.toLowerCase().contains("connect")) {
                msg = "Could not reach the ESMFold API.\n"
                    + "Please check your internet connection and try again.";
            } else {
                msg = raw != null ? raw : "Unknown error";
            }
            setStatus("ESMFold failed - " + msg.lines().findFirst().orElse("see error dialog"));
            System.err.println("[ESMFold] Failed: " + ex);
            showError("ESMFold Error", msg);
        });

        Thread t = new Thread(task, "esmfold-thread");
        t.setDaemon(true);
        t.start();
    }

    // ── Long sequences: AlphaFold DB fallback ──────────────────────────────

    /**
     * Shown when the sequence exceeds the ESMFold length limit. ESMFold folds the
     * (possibly edited) sequence live but only up to {@code ESMFOLD_MAX_LENGTH}
     * residues, so for a longer protein we offer the precomputed AlphaFold DB
     * structure of the original UniProt entry instead - clearly explaining that any
     * edits are not reflected in it. If no accession is in the header, no database
     * structure exists and we say so; the user can still view and edit the sequence.
     */
    private void offerAlphaFold(int length) {
        String accession = AlphaFoldService.parseAccession(currentSequence.getHeader());

        if (accession == null) {
            showError("Sequence Too Long for ESMFold",
                "This sequence has " + length + " residues, above the ESMFold limit of "
                + ESMFOLD_MAX_LENGTH + ".\n\n"
                + "ESMFold (which folds your edited sequence live) cannot handle it, and no "
                + "UniProt accession was found in the FASTA header, so no AlphaFold database "
                + "structure can be loaded either.\n\n"
                + "You can still view and edit the amino-acid sequence. To see a 3D structure, "
                + "use a sequence of at most " + ESMFOLD_MAX_LENGTH + " residues.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Sequence too long for ESMFold");
        alert.setHeaderText("This sequence has " + length + " residues "
            + "(ESMFold limit: " + ESMFOLD_MAX_LENGTH + ")");
        alert.getDialogPane().setPrefWidth(580);
        alert.setContentText(
            "ESMFold folds your (possibly edited) sequence live, but only up to "
            + ESMFOLD_MAX_LENGTH + " residues - so it cannot fold this protein.\n\n"
            + "Instead, I can load the precomputed AlphaFold DB structure for " + accession + ".\n\n"
            + "What this means:\n"
            + "• It is a downloaded, precomputed prediction for the ORIGINAL protein "
            + accession + " - not a live fold.\n"
            + "• Any edits you made to the sequence are NOT reflected in it.\n"
            + "• It is a computational model, not an experimental structure.\n\n"
            + "Load the AlphaFold DB structure?");

        ButtonType load = new ButtonType("Load AlphaFold DB structure", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(load, cancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == load) {
            loadAlphaFold(accession);
        }
    }

    /** Downloads and displays the AlphaFold DB structure for {@code accession}. */
    private void loadAlphaFold(String accession) {
        controller.getEsmFoldButton().setDisable(true);
        showFoldingSpinner(true);
        setStatus("Loading AlphaFold DB structure for " + accession + "…");

        Task<ProteinStructure> task = new Task<>() {
            @Override protected ProteinStructure call() throws Exception {
                String pdbText = alphaFoldService.fetchByAccession(accession);
                return pdbParser.parseContent("AlphaFold " + accession, pdbText);
            }
        };

        task.setOnSucceeded(evt -> {
            controller.getEsmFoldButton().setDisable(false);
            showFoldingSpinner(false);
            ProteinStructure result = task.getValue();
            if (result == null || result.getAtomCount() == 0) {
                setStatus("AlphaFold DB returned an empty structure for " + accession + ".");
                return;
            }
            latestStructure = result;
            protein3DPresenter.showStructure(latestStructure);
            setFoldReady(false);
            refreshEsmFoldState();
            setStatus("Loaded AlphaFold DB structure for " + accession + " - "
                + result.getResidueCount() + " residues (original sequence, edits not reflected)");
        });

        task.setOnFailed(evt -> {
            controller.getEsmFoldButton().setDisable(false);
            showFoldingSpinner(false);
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            setStatus("AlphaFold DB load failed - " + msg);
            showError("AlphaFold DB Error", msg);
        });

        Thread t = new Thread(task, "alphafold-thread");
        t.setDaemon(true);
        t.start();
    }

    private void showFoldingSpinner(boolean show) {
        if (show) {
            if (foldingSpinner == null) {
                foldingSpinner = new ProgressIndicator();
                foldingSpinner.setMaxSize(16, 16);
                foldingSpinner.setStyle("-fx-progress-color: #58A6FF;");
            }
            // Insert spinner next to the ESMFold button in the toolbar
            javafx.scene.layout.HBox toolbar = (javafx.scene.layout.HBox) controller.getEsmFoldButton().getParent();
            int idx = toolbar.getChildren().indexOf(controller.getEsmFoldButton());
            if (!toolbar.getChildren().contains(foldingSpinner)) {
                toolbar.getChildren().add(idx + 1, foldingSpinner);
            }
        } else {
            if (foldingSpinner != null && foldingSpinner.getParent() != null) {
                ((javafx.scene.layout.Pane) foldingSpinner.getParent()).getChildren().remove(foldingSpinner);
            }
        }
    }

    // ── Add Conformation ───────────────────────────────────────────────────

    private void onAddConformation() {
        if (currentSequence == null) return;
        String desc = descriptionField.getText().trim();
        Conformation c = new Conformation(currentSequence.toSequenceString(), latestStructure, desc);
        conformationStore.add(c);
        descriptionField.clear();
        setStatus("Conformation added (" + conformationStore.getConformations().size() + " total)");
        pushConformationUndo(c);
    }

    // ── Fold from alignment (Req 9) ────────────────────────────────────────

    public void foldAndAddConformation(String name, String sequence) {
        if (sequence.isEmpty()) return;

        if (sequence.length() > ESMFOLD_MAX_LENGTH) {
            // Too long for ESMFold - but these are unedited database sequences, so their
            // real AlphaFold DB structure is the correct thing to show (no caveat needed).
            String accession = AlphaFoldService.parseAccession(name);
            if (accession != null) {
                loadAlphaFoldAsConformation(name, sequence, accession);
            } else {
                showError("Sequence Too Long",
                    "\"" + name + "\" has " + sequence.length() + " residues, above the ESMFold "
                    + "limit of " + ESMFOLD_MAX_LENGTH + ", and no UniProt accession was found to "
                    + "load an AlphaFold DB structure.");
            }
            return;
        }

        if (controller.getEsmFoldButton().isDisabled()) {
            showError("Busy", "ESMFold is already running. Please wait for it to complete.");
            return;
        }

        ProteinStructure cached = esmFoldCache.get(sequence);
        if (cached != null) {
            Conformation conf = new Conformation(sequence, cached, name);
            conformationStore.add(conf);
            setStatus("Folded (cached): " + name + " - added as conformation");
            pushConformationUndo(conf);
            return;
        }

        controller.getEsmFoldButton().setDisable(true);
        showFoldingSpinner(true);
        setStatus("Folding \"" + name + "\" (" + sequence.length() + " aa) via ESMFold…");

        Task<ProteinStructure> task = new Task<>() {
            @Override protected ProteinStructure call() throws Exception {
                for (int attempt = 1; attempt <= ESMFOLD_MAX_RETRIES; attempt++) {
                    if (attempt > 1) {
                        final int a = attempt;
                        Platform.runLater(() -> setStatus(
                            "ESMFold: retry " + a + "/" + ESMFOLD_MAX_RETRIES
                            + " for \"" + name + "\"…"));
                        Thread.sleep(attempt * 5_000L);
                    }
                    try {
                        String pdbText = esmFoldService.fold(sequence);
                        return pdbParser.parseContent(name, pdbText);
                    } catch (java.io.IOException e) {
                        String m = e.getMessage();
                        boolean retryable = m != null && (m.contains("504") || m.contains("503")
                            || m.contains("timed out") || m.contains("timeout")
                            || m.contains("SocketTimeout") || m.contains("connect"));
                        if (!retryable || attempt == ESMFOLD_MAX_RETRIES) throw e;
                    }
                }
                throw new java.io.IOException("unreachable");
            }
        };

        task.setOnSucceeded(evt -> {
            ProteinStructure structure = task.getValue();
            controller.getEsmFoldButton().setDisable(false);
            showFoldingSpinner(false);
            if (structure == null || structure.getAtomCount() == 0) {
                setStatus("ESMFold returned empty structure for \"" + name + "\"");
                return;
            }
            esmFoldCache.put(sequence, structure);
            Conformation conf = new Conformation(sequence, structure, name);
            conformationStore.add(conf);
            setStatus("Folded: \"" + name + "\" - " + structure.getResidueCount()
                + " residues · added as conformation");
            pushConformationUndo(conf);
        });

        task.setOnFailed(evt -> {
            controller.getEsmFoldButton().setDisable(false);
            showFoldingSpinner(false);
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            setStatus("Fold failed for \"" + name + "\": " + msg);
            showError("ESMFold Error", "Could not fold \"" + name + "\":\n" + msg);
        });

        new Thread(task, "esmfold-alignment-thread").start();
    }

    /**
     * Alignment-fold fallback for a related sequence longer than the ESMFold limit:
     * downloads its real AlphaFold DB structure by accession and stores it as a
     * conformation. Unlike an edited sequence, these BLAST/Swiss-Prot hits are the
     * genuine database entries, so the AlphaFold structure matches them exactly.
     */
    private void loadAlphaFoldAsConformation(String name, String sequence, String accession) {
        if (controller.getEsmFoldButton().isDisabled()) {
            showError("Busy", "A fold is already running. Please wait for it to complete.");
            return;
        }
        controller.getEsmFoldButton().setDisable(true);
        showFoldingSpinner(true);
        setStatus("Loading AlphaFold DB structure for " + accession + "…");

        Task<ProteinStructure> task = new Task<>() {
            @Override protected ProteinStructure call() throws Exception {
                String pdbText = alphaFoldService.fetchByAccession(accession);
                return pdbParser.parseContent("AlphaFold " + accession, pdbText);
            }
        };

        task.setOnSucceeded(evt -> {
            controller.getEsmFoldButton().setDisable(false);
            showFoldingSpinner(false);
            ProteinStructure structure = task.getValue();
            if (structure == null || structure.getAtomCount() == 0) {
                setStatus("AlphaFold DB returned an empty structure for " + accession + ".");
                return;
            }
            Conformation conf = new Conformation(sequence, structure, name);
            conformationStore.add(conf);
            setStatus("Loaded AlphaFold DB structure for \"" + name + "\" - "
                + structure.getResidueCount() + " residues · added as conformation");
            pushConformationUndo(conf);
        });

        task.setOnFailed(evt -> {
            controller.getEsmFoldButton().setDisable(false);
            showFoldingSpinner(false);
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            setStatus("AlphaFold DB load failed for \"" + name + "\" - " + msg);
            showError("AlphaFold DB Error", msg);
        });

        Thread t = new Thread(task, "alphafold-alignment-thread");
        t.setDaemon(true);
        t.start();
    }

    // ── AI natural-language editing (Req 1-9) ──────────────────────────────

    /** Opens the modal AI-editing dialog for the current sequence. */
    private void onAiEdit() {
        if (currentSequence == null) return;
        try {
            Window owner = aiEditButton.getScene().getWindow();
            new NlEditView(owner, currentSequence, this::applyAiEdits).show();
        } catch (IOException ex) {
            showError("Dialog Error", "Could not open the AI editing dialog: " + ex.getMessage());
        }
    }

    /**
     * Applies the validated, user-approved edits returned by the dialog. The whole
     * batch is a single undo step; afterwards the edited sequence is folded and the
     * resulting conformation is stored in the history (Req 8 &amp; 9). The AI never
     * touches the sequence directly - only these already-validated edits are applied.
     */
    private void applyAiEdits(List<SequenceEdit> edits, String request) {
        if (currentSequence == null || edits.isEmpty()) return;

        final Sequence before = currentSequence;
        final Sequence after  = EditApplier.apply(before, edits);
        currentSequence = after;
        selectionModel.clearSelection();
        displaySequence();
        markStructureStale();
        setStaleStatus("Applied " + edits.size() + " AI edit(s)");

        undoManager.push(new UndoManager.Edit() {
            public void undo() {
                currentSequence = before;
                selectionModel.clearSelection();
                displaySequence();
                markStructureStale();
                setStaleStatus("Undo: reverted AI edits");
            }
            public void redo() {
                currentSequence = after;
                selectionModel.clearSelection();
                displaySequence();
                markStructureStale();
                setStaleStatus("Redo: re-applied AI edits");
            }
            public String label() { return "AI edit (" + edits.size() + ")"; }
        });

        String desc = request.isBlank()
            ? "AI edit"
            : "AI: " + (request.length() > 40 ? request.substring(0, 40) + "…" : request);
        foldEditedAndStore(desc);
    }

    /**
     * Folds the current (AI-edited) sequence via ESMFold, shows it in 3D and stores
     * the result as a new conformation in the history. Mirrors {@link #onEsmFold()}
     * but additionally persists the conformation (Req 8 &amp; 9).
     */
    private void foldEditedAndStore(String description) {
        String seq = currentSequence.toFoldableSequenceString();
        if (seq.isEmpty()) {
            setStatus("Edited sequence is empty - nothing to fold.");
            return;
        }
        if (seq.length() > ESMFOLD_MAX_LENGTH) {
            showError("Sequence Too Long",
                "The edited sequence has " + seq.length() + " residues.\n"
                + "ESMFold supports at most " + ESMFOLD_MAX_LENGTH + " residues.");
            return;
        }

        // Cache hit: fold instantly, show and store
        ProteinStructure cached = esmFoldCache.get(seq);
        if (cached != null) {
            latestStructure = cached;
            protein3DPresenter.showStructure(latestStructure);
            setFoldReady(false);
            Conformation conf = new Conformation(currentSequence.toSequenceString(), cached, description);
            conformationStore.add(conf);
            pushConformationUndo(conf);
            setStatus("Folded (cached) & stored: " + description);
            return;
        }

        controller.getEsmFoldButton().setDisable(true);
        showFoldingSpinner(true);
        setStatus("Folding edited sequence (" + seq.length() + " aa) via ESMFold…");

        Task<ProteinStructure> task = new Task<>() {
            @Override protected ProteinStructure call() throws Exception {
                for (int attempt = 1; attempt <= ESMFOLD_MAX_RETRIES; attempt++) {
                    if (attempt > 1) {
                        final int a = attempt;
                        Platform.runLater(() -> setStatus(
                            "ESMFold: retry " + a + "/" + ESMFOLD_MAX_RETRIES + " for edited sequence…"));
                        Thread.sleep(attempt * 5_000L);
                    }
                    try {
                        String pdbText = esmFoldService.fold(seq);
                        return pdbParser.parseContent(description, pdbText);
                    } catch (java.io.IOException e) {
                        String m = e.getMessage();
                        boolean retryable = m != null && (m.contains("504") || m.contains("503")
                            || m.contains("timed out") || m.contains("timeout")
                            || m.contains("SocketTimeout") || m.contains("connect"));
                        if (!retryable || attempt == ESMFOLD_MAX_RETRIES) throw e;
                    }
                }
                throw new java.io.IOException("unreachable");
            }
        };

        task.setOnSucceeded(evt -> {
            controller.getEsmFoldButton().setDisable(false);
            showFoldingSpinner(false);
            ProteinStructure result = task.getValue();
            if (result == null || result.getAtomCount() == 0) {
                setStatus("ESMFold returned an empty structure - the conformation was not stored.");
                return;
            }
            latestStructure = result;
            esmFoldCache.put(seq, result);
            protein3DPresenter.showStructure(latestStructure);
            setFoldReady(false);
            Conformation conf = new Conformation(currentSequence.toSequenceString(), result, description);
            conformationStore.add(conf);
            pushConformationUndo(conf);
            setStatus("Folded & stored: " + description + " - "
                + result.getResidueCount() + " residues, " + result.getAtomCount() + " atoms");
        });

        task.setOnFailed(evt -> {
            controller.getEsmFoldButton().setDisable(false);
            showFoldingSpinner(false);
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            setStatus("Fold failed for edited sequence - " + msg);
            showError("ESMFold Error", "Could not fold the edited sequence:\n" + msg);
        });

        Thread t = new Thread(task, "esmfold-aiedit-thread");
        t.setDaemon(true);
        t.start();
    }

    private void pushConformationUndo(Conformation conf) {
        undoManager.push(new UndoManager.Edit() {
            public void undo() {
                conformationStore.remove(conf);
                String label = conf.getDescription().isBlank() ? "conformation" : "\"" + conf.getDescription() + "\"";
                setStatus("Undo: removed " + label);
            }
            public void redo() {
                conformationStore.add(conf);
                String label = conf.getDescription().isBlank() ? "conformation" : "\"" + conf.getDescription() + "\"";
                setStatus("Redo: added " + label + " back");
            }
            public String label() { return "Add conformation"; }
        });
    }

    // ── BLAST search ──────────────────────────────────────────────────────

    private void onFindRelated() {
        if (currentSequence == null) return;

        String seq = currentSequence.toFoldableSequenceString();
        if (seq.isEmpty()) {
            showError("Empty Sequence", "The sequence is empty - nothing to search for.");
            return;
        }

        int maxHits = controller.getBlastHitsComboBox().getValue();
        controller.getFindRelatedButton().setDisable(true);
        showBlastSpinner(true);

        Task<List<BlastHit>> task = new Task<>() {
            @Override
            protected List<BlastHit> call() throws Exception {
                return blastService.search(seq, maxHits,
                    msg -> Platform.runLater(() -> setStatus(msg)));
            }
        };

        task.setOnSucceeded(evt -> {
            List<BlastHit> results = task.getValue();
            lastBlastResults.clear();
            lastBlastResults.addAll(results);
            controller.getFindRelatedButton().setDisable(false);
            showBlastSpinner(false);
            setStatus("Found " + results.size() + " related sequences - aligning…");
            System.out.println("[BLAST] Found " + results.size() + " hits");
            results.forEach(h -> System.out.println("  " + h));

            if (!results.isEmpty()) {
                alignmentPresenter.runAlignment(
                    currentSequence.getHeader(),
                    currentSequence.toFoldableSequenceString(),
                    results);
            }
        });

        task.setOnFailed(evt -> {
            controller.getFindRelatedButton().setDisable(false);
            showBlastSpinner(false);
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            setStatus("BLAST search failed - " + msg);
            showError("BLAST Error", msg);
            System.err.println("[BLAST] Failed: " + ex);
        });

        Thread t = new Thread(task, "blast-thread");
        t.setDaemon(true);
        t.start();
    }

    private ProgressIndicator blastSpinner;

    private void showBlastSpinner(boolean show) {
        if (show) {
            if (blastSpinner == null) {
                blastSpinner = new ProgressIndicator();
                blastSpinner.setMaxSize(16, 16);
                blastSpinner.setStyle("-fx-progress-color: #58A6FF;");
            }
            javafx.scene.layout.HBox toolbar =
                (javafx.scene.layout.HBox) controller.getFindRelatedButton().getParent();
            int idx = toolbar.getChildren().indexOf(controller.getFindRelatedButton());
            if (!toolbar.getChildren().contains(blastSpinner))
                toolbar.getChildren().add(idx + 1, blastSpinner);
        } else if (blastSpinner != null && blastSpinner.getParent() != null) {
            ((javafx.scene.layout.Pane) blastSpinner.getParent())
                .getChildren().remove(blastSpinner);
        }
    }

    public List<BlastHit> getLastBlastResults() {
        return Collections.unmodifiableList(lastBlastResults);
    }

    // ── Sequence display ───────────────────────────────────────────────────

    private void displaySequence() {
        controller.getSequenceBox().getChildren().clear();
        residueButtons.clear();
        if (currentSequence == null) return;

        for (int i = 0; i < currentSequence.length(); i++) {
            final int idx = i;
            char aa = currentSequence.get(i);
            Button btn = new Button(aa + "\n" + (i + 1));
            btn.getStyleClass().add(CLS_DEFAULT);
            btn.setTooltip(new Tooltip(AminoAcidNames.tooltip(aa)));

            // Left-click: toggle this residue; also anchors a potential drag
            btn.setOnMousePressed(e -> {
                if (e.isPrimaryButtonDown()) {
                    dragStartIdx = idx;
                    selectionModel.toggle(idx);
                    e.consume();
                }
            });
            // Start a full-drag so sibling buttons receive mouseDragEntered events
            btn.setOnDragDetected(e -> {
                btn.startFullDrag();
                e.consume();
            });
            // Drag over other buttons: extend range from drag origin
            btn.setOnMouseDragEntered(e -> {
                if (dragStartIdx >= 0) selectionModel.setRange(dragStartIdx, idx);
                e.consume();
            });

            // Right-click context menu: Insert Before / Insert After / Delete
            btn.setContextMenu(buildResidueContextMenu(idx));

            residueButtons.add(btn);
            controller.getSequenceBox().getChildren().add(btn);
        }
        updateHighlights();
        syncButtons();
    }

    private void refreshButton(int idx) {
        if (idx < 0 || idx >= residueButtons.size()) return;
        residueButtons.get(idx).setText(currentSequence.get(idx) + "\n" + (idx + 1));
    }

    private void updateHighlights() {
        for (int i = 0; i < residueButtons.size(); i++) {
            Button btn = residueButtons.get(i);
            btn.getStyleClass().removeAll(CLS_DEFAULT, CLS_SELECTED);
            btn.getStyleClass().add(selectionModel.isSelected(i) ? CLS_SELECTED : CLS_DEFAULT);
        }
    }

    /** Right-click context menu for a residue button at {@code idx}. */
    private ContextMenu buildResidueContextMenu(int idx) {
        MenuItem insertBefore = new MenuItem("Insert Before…");
        MenuItem insertAfter  = new MenuItem("Insert After…");
        MenuItem delete       = new MenuItem("Delete");

        insertBefore.setOnAction(e -> {
            selectionModel.setSingle(idx);
            promptAndInsert(idx);          // inserts at idx → pushes current AA to the right
        });
        insertAfter.setOnAction(e -> {
            selectionModel.setSingle(idx);
            promptAndInsert(idx + 1);      // inserts right after idx
        });
        delete.setOnAction(e -> {
            selectionModel.setSingle(idx);
            onDeleteSelected();
        });

        return new ContextMenu(insertBefore, insertAfter, new SeparatorMenuItem(), delete);
    }

    /**
     * Opens a choice dialog so the user can pick an amino acid,
     * then inserts it at {@code insertIdx}.
     */
    private void promptAndInsert(int insertIdx) {
        if (currentSequence == null) return;

        List<Character> choices = new ArrayList<>();
        for (char aa : AMINO_ACIDS) choices.add(aa);
        choices.add('*');

        ChoiceDialog<Character> dialog = new ChoiceDialog<>('A', choices);
        dialog.setTitle("Insert Residue");
        dialog.setHeaderText("Insert at position " + (insertIdx + 1));
        dialog.setContentText("Amino acid:");
        dialog.showAndWait().ifPresent(aa -> onInsertAt(insertIdx, aa));
    }

    /** Core insert logic (used by toolbar button and context menu). */
    private void onInsertAt(int insertIdx, char aa) {
        currentSequence.insert(insertIdx, aa);
        displaySequence();
        selectionModel.setSingle(insertIdx);
        markStructureStale();
        setStaleStatus("Inserted '" + aa + "' at position " + (insertIdx + 1));

        undoManager.push(new UndoManager.Edit() {
            public void undo() { currentSequence.delete(insertIdx); displaySequence(); markStructureStale(); setStaleStatus("Undo: removed inserted '" + aa + "'"); }
            public void redo() { currentSequence.insert(insertIdx, aa); displaySequence(); selectionModel.setSingle(insertIdx); markStructureStale(); setStaleStatus("Redo: re-inserted '" + aa + "' at pos " + (insertIdx + 1)); }
            public String label() { return "Insert '" + aa + "'"; }
        });
    }

    private void syncButtons() {
        boolean hasSeq = currentSequence != null;
        boolean hasSel = !selectionModel.isEmpty();
        boolean hasAA  = controller.getMutationComboBox().getValue() != null;

        controller.getApplyMutationButton().setDisable(!hasSeq || !hasSel || !hasAA);
        controller.getInsertButton()       .setDisable(!hasSeq || !hasSel || !hasAA);
        controller.getDeleteButton()       .setDisable(!hasSeq || !hasSel);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void setStatus(String text) {
        controller.getStatusLabel().setText(text);
    }

    /** Shows an action summary plus a persistent "run ESMFold" reminder. */
    private void setStaleStatus(String action) {
        controller.getStatusLabel().setText(action + "  ·  Run ESMFold again to see your changes");
    }

    private static void showError(String header, String content) {
        Runnable show = () -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
            alert.setTitle("Error");
            alert.setHeaderText(header);
            alert.showAndWait();
        };
        if (Platform.isFxApplicationThread()) show.run();
        else Platform.runLater(show);
    }
}
