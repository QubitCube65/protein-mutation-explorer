package org.example.project.window;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import org.example.project.model.UndoManager;

/** Wires the menu actions (Undo, Redo, Quit, Help) to the shared UndoManager. */
public final class WindowPresenter {

    public WindowPresenter(WindowController controller, UndoManager undoManager) {
        controller.getUndoMenuItem().setOnAction(e -> undoManager.undo());
        controller.getRedoMenuItem().setOnAction(e -> undoManager.redo());
        controller.getQuitMenuItem().setOnAction(e -> Platform.exit());
        controller.getHelpMenuItem().setOnAction(e -> showHelp());

        controller.getUndoMenuItem().disableProperty().bind(undoManager.canUndoProperty().not());
        controller.getRedoMenuItem().disableProperty().bind(undoManager.canRedoProperty().not());

        // Labelled history: the menu shows e.g. "Undo Mutation" / "Redo Delete 3 residue(s)"
        controller.getUndoMenuItem().textProperty().bind(undoManager.undoTextProperty());
        controller.getRedoMenuItem().textProperty().bind(undoManager.redoTextProperty());
    }

    private static void showHelp() {
        String text = """
                1.  Load FASTA
                    Click "Load FASTA…" to open a protein sequence file.
                    The sequence appears as clickable residue tiles.

                2.  Edit sequence
                    Click a residue tile to select it (drag for a range).
                    Choose an amino acid from the "AA:" dropdown, then:
                      • Mutate  - replace selected residue(s)
                      • Insert  - insert the chosen AA after the selection
                      • Delete  - remove selected residue(s)
                    Ctrl+Z / Ctrl+Y to undo / redo changes.

                3.  Predict 3D structure  (Structure tab)
                    Click "Run ESMFold" to fold the current sequence.
                    The interactive 3D viewer opens in the Structure tab.
                    Zoom with the scroll wheel; rotate by dragging.

                4.  Find related sequences
                    Click "Find Related" to run NCBI BLAST against SwissProt.
                    Use the "Hits:" dropdown to control how many results to fetch.
                    Results are then aligned with Clustal Omega automatically.

                5.  Alignment tab
                    Switch to the Alignment tab to view the multiple sequence
                    alignment (MSA).
                      • Click a column to highlight the matching residue in the
                        editor and 3D viewer.
                      • Click a sequence name on the left to show its amino acid
                        composition in the Statistics panel on the right.
                      • Columns with a subtle green tint are highly conserved
                        (≥ 95 % of sequences share the same amino acid).

                6.  Save conformations
                    After folding, enter a description and click "Add Conformation"
                    to save the current sequence + structure.
                    Saved conformations appear as cards - click one to restore it.
                """;

        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefSize(560, 420);
        area.setStyle(
            "-fx-font-family: 'Consolas', 'Courier New', monospace; "
            + "-fx-font-size: 11px; "
            + "-fx-control-inner-background: #161B22; "
            + "-fx-text-fill: #CDD9E5;");

        Alert alert = new Alert(Alert.AlertType.NONE, "", ButtonType.CLOSE);
        alert.setTitle("How to use - Protein Mutation Explorer");
        alert.setHeaderText("Quick Guide");
        alert.getDialogPane().setContent(area);
        alert.getDialogPane().setStyle("-fx-background-color: #0D1117;");
        alert.showAndWait();
    }
}
