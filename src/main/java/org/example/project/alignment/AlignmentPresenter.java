package org.example.project.alignment;

import javafx.application.Platform;
import javafx.collections.SetChangeListener;
import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.project.model.*;
import org.example.project.statistics.StatisticsPresenter;

import java.util.*;
import java.util.function.BiConsumer;

public final class AlignmentPresenter {

    private static final String CELL_BASE =
        "-fx-font-family: monospace; -fx-font-size: 11; "
        + "-fx-min-width: 14; -fx-max-width: 14; -fx-pref-width: 14; "
        + "-fx-alignment: center; -fx-padding: 1 0; ";
    private static final String NAME_BASE =
        "-fx-font-family: monospace; -fx-font-size: 11; "
        + "-fx-min-width: 130; -fx-max-width: 130; -fx-pref-width: 130; "
        + "-fx-padding: 1 6 1 2; ";
    private static final String HEADER_BASE =
        "-fx-font-family: monospace; -fx-font-size: 9; "
        + "-fx-min-width: 14; -fx-max-width: 14; -fx-pref-width: 14; "
        + "-fx-alignment: center; -fx-padding: 0; ";

    private final AlignmentController controller;
    private final ResidueSelectionModel selectionModel;
    private StatisticsPresenter statisticsPresenter;
    private BiConsumer<String, String> foldCallback;
    private final ClustalOmegaService clustalService = new ClustalOmegaService();

    private AlignmentResult currentAlignment;
    private Label[][] cellLabels;
    private Label[] nameLabels;
    private Label[] headerLabels;
    private final Set<Integer> selectedRows    = new LinkedHashSet<>();
    private final Set<Integer> selectedColumns = new LinkedHashSet<>();

    // Conservation score per column (Req 6)
    private double[] conservationScores;

    // Alignment column ↔ query residue index mapping (gaps cause misalignment)
    private int[] colToResidue;
    private int[] residueToCol;

    // Guards against circular sync updates
    private boolean syncingFromAlignment;
    private boolean syncingFromEditor;

    public void setStatisticsPresenter(StatisticsPresenter statisticsPresenter) {
        this.statisticsPresenter = statisticsPresenter;
    }

    public void setFoldCallback(BiConsumer<String, String> callback) {
        this.foldCallback = callback;
    }

    public AlignmentPresenter(AlignmentController controller,
                              ResidueSelectionModel selectionModel) {
        this.controller     = controller;
        this.selectionModel = selectionModel;

        controller.getFoldSelectedButton().setOnAction(e -> onFoldSelected());

        // Toggle per-residue letter colouring (background/conservation stays untouched)
        controller.getColorResiduesCheckBox().selectedProperty()
            .addListener((obs, o, n) -> refreshAllStyles());

        selectionModel.selectedIndicesProperty().addListener(
            (SetChangeListener<Integer>) change -> {
                if (syncingFromAlignment) return;
                if (residueToCol == null || currentAlignment == null) return;
                syncingFromEditor = true;
                selectedColumns.clear();
                for (int idx : selectionModel.selectedIndicesProperty()) {
                    if (idx >= 0 && idx < residueToCol.length) {
                        selectedColumns.add(residueToCol[idx]);
                    }
                }
                refreshAllStyles();
                syncingFromEditor = false;
            });
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Clears the alignment view and its statistics. Called when a different
     * conformation is loaded, since an alignment always belongs to the specific
     * sequence it was built for and no longer applies to the new one.
     */
    public void clear() {
        controller.getAlignmentGrid().getChildren().clear();
        cellLabels         = null;
        nameLabels         = null;
        headerLabels       = null;
        currentAlignment   = null;
        conservationScores = null;
        colToResidue       = null;
        residueToCol       = null;
        selectedRows.clear();
        selectedColumns.clear();
        if (statisticsPresenter != null) statisticsPresenter.clear();
        updateFoldButton();
        setStatus("Run “Find Related” to build an alignment for the current sequence.");
    }


    public void runAlignment(String queryHeader, String querySequence,
                             List<BlastHit> hits) {

        controller.getAlignmentGrid().getChildren().clear();
        cellLabels         = null;
        nameLabels         = null;
        headerLabels       = null;
        currentAlignment   = null;
        conservationScores = null;
        colToResidue       = null;
        residueToCol       = null;
        selectedRows.clear();
        selectedColumns.clear();
        if (statisticsPresenter != null) statisticsPresenter.clear();

        if (hits.isEmpty()) {
            setStatus("No related sequences to align");
            return;
        }

        String fasta = buildFasta(queryHeader, querySequence, hits);
        setStatus("Aligning " + (hits.size() + 1) + " sequences with Clustal Omega…");
        showSpinner(true);

        Task<AlignmentResult> task = new Task<>() {
            @Override
            protected AlignmentResult call() throws Exception {
                return clustalService.align(fasta,
                    msg -> Platform.runLater(() -> setStatus(msg)));
            }
        };

        task.setOnSucceeded(evt -> {
            showSpinner(false);
            AlignmentResult result = task.getValue();
            currentAlignment = result;
            computeConservationScores(result);
            buildColumnMapping(result);
            displayAlignment(result);
            setStatus(result.getSequenceCount() + " sequences, "
                + result.getAlignmentLength() + " columns");
            if (statisticsPresenter != null) statisticsPresenter.showAlignment(result);
            updateFoldButton();
        });

        task.setOnFailed(evt -> {
            showSpinner(false);
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            setStatus("Alignment failed");
            System.err.println("[ClustalO] Failed: " + ex);
        });

        Thread t = new Thread(task, "clustal-thread");
        t.setDaemon(true);
        t.start();
    }

    public AlignmentResult getCurrentAlignment() { return currentAlignment; }
    public Set<Integer> getSelectedRows()    { return Collections.unmodifiableSet(selectedRows); }
    public Set<Integer> getSelectedColumns() { return Collections.unmodifiableSet(selectedColumns); }

    private void onFoldSelected() {
        if (selectedRows.isEmpty() || currentAlignment == null || foldCallback == null) return;
        int row = selectedRows.iterator().next();
        String name = currentAlignment.getName(row);
        String seq  = currentAlignment.getAlignedSequence(row).replace("-", "");
        foldCallback.accept(name, seq);
    }

    private void updateFoldButton() {
        boolean enabled = !selectedRows.isEmpty() && currentAlignment != null && foldCallback != null;
        controller.getFoldSelectedButton().setDisable(!enabled);
    }

    // ── Conservation scores (Req 6) ───────────────────────────────────────

    private void computeConservationScores(AlignmentResult result) {
        int cols = result.getAlignmentLength();
        int rows = result.getSequenceCount();
        conservationScores = new double[cols];

        for (int c = 0; c < cols; c++) {
            Map<Character, Integer> freq = new HashMap<>();
            for (int r = 0; r < rows; r++) {
                char ch = result.getChar(r, c);
                if (ch != '-') freq.merge(ch, 1, Integer::sum);
            }
            if (!freq.isEmpty()) {
                int maxFreq = Collections.max(freq.values());
                conservationScores[c] = (double) maxFreq / rows;
            }
        }
    }

    // ── Column ↔ residue mapping ──────────────────────────────────────────

    private void buildColumnMapping(AlignmentResult result) {
        int cols = result.getAlignmentLength();
        colToResidue = new int[cols];
        String querySeq = result.getAlignedSequence(0);

        int residueCount = 0;
        for (int c = 0; c < cols; c++) {
            if (querySeq.charAt(c) != '-') residueCount++;
        }
        residueToCol = new int[residueCount];

        int residueIdx = 0;
        for (int c = 0; c < cols; c++) {
            if (querySeq.charAt(c) != '-') {
                colToResidue[c] = residueIdx;
                residueToCol[residueIdx] = c;
                residueIdx++;
            } else {
                colToResidue[c] = -1;
            }
        }
    }

    private void syncColumnsToResidueSelection() {
        if (syncingFromEditor || selectionModel == null || colToResidue == null) return;

        List<Integer> residues = new ArrayList<>();
        for (int col : selectedColumns) {
            int idx = colToResidue[col];
            if (idx >= 0) residues.add(idx);
        }
        if (residues.isEmpty()) return; // all selected columns are gaps - leave editor selection alone

        syncingFromAlignment = true;
        selectionModel.clearSelection();
        residues.forEach(idx -> selectionModel.selectedIndicesProperty().add(idx));
        syncingFromAlignment = false;
    }

    // ── FASTA builder ─────────────────────────────────────────────────────

    private String buildFasta(String queryHeader, String querySequence,
                              List<BlastHit> hits) {
        StringBuilder sb = new StringBuilder();
        String name = (queryHeader == null || queryHeader.isBlank())
            ? "Query" : queryHeader;
        sb.append(">").append(name).append("\n");
        sb.append(querySequence).append("\n");

        for (BlastHit hit : hits) {
            sb.append(">").append(hit.getAccession()).append("\n");
            sb.append(hit.getSequence()).append("\n");
        }
        return sb.toString();
    }

    // ── Grid rendering ────────────────────────────────────────────────────

    private void displayAlignment(AlignmentResult result) {
        VBox grid = controller.getAlignmentGrid();
        grid.getChildren().clear();

        int rows = result.getSequenceCount();
        int cols = result.getAlignmentLength();
        cellLabels   = new Label[rows][cols];
        nameLabels   = new Label[rows];
        headerLabels = new Label[cols];

        grid.getChildren().add(buildHeaderRow(cols));

        for (int r = 0; r < rows; r++) {
            final int row = r;
            HBox rowBox = new HBox(0);
            String seq = result.getAlignedSequence(r);

            Label nameLabel = new Label(truncate(result.getName(r), 18));
            nameLabel.setStyle(computeNameStyle(r));
            nameLabel.setCursor(Cursor.HAND);
            nameLabel.setOnMouseClicked(e -> {
                if (e.isControlDown()) {
                    toggle(selectedRows, row);
                } else {
                    selectedRows.clear();
                    selectedRows.add(row);
                }
                refreshAllStyles();
                if (statisticsPresenter != null && !selectedRows.isEmpty()) {
                    statisticsPresenter.showRow(currentAlignment,
                        selectedRows.iterator().next());
                }
            });
            nameLabels[r] = nameLabel;
            rowBox.getChildren().add(nameLabel);

            for (int c = 0; c < cols; c++) {
                final int col = c;
                char ch = seq.charAt(c);
                Label cell = new Label(String.valueOf(ch));
                cell.setStyle(computeCellStyle(r, c));
                cell.setCursor(Cursor.HAND);
                cell.setOnMouseClicked(e -> {
                    if (e.isControlDown()) {
                        toggle(selectedRows, row);
                        toggle(selectedColumns, col);
                    } else {
                        selectedRows.clear();
                        selectedRows.add(row);
                        selectedColumns.clear();
                        selectedColumns.add(col);
                    }
                    syncColumnsToResidueSelection();
                    refreshAllStyles();
                });
                cellLabels[r][c] = cell;
                rowBox.getChildren().add(cell);
            }
            grid.getChildren().add(rowBox);
        }
    }

    private HBox buildHeaderRow(int cols) {
        HBox header = new HBox(0);
        Label spacer = new Label("");
        spacer.setStyle(NAME_BASE + "-fx-text-fill: transparent;");
        header.getChildren().add(spacer);

        for (int c = 0; c < cols; c++) {
            final int col = c;
            String text = ((c + 1) % 10 == 0) ? String.valueOf(c + 1) : "";
            Label lbl = new Label(text);
            lbl.setStyle(computeHeaderStyle(c));
            lbl.setCursor(Cursor.HAND);
            lbl.setOnMouseClicked(e -> {
                if (e.isControlDown()) {
                    toggle(selectedColumns, col);
                } else {
                    selectedColumns.clear();
                    selectedColumns.add(col);
                }
                syncColumnsToResidueSelection();
                refreshAllStyles();
            });
            headerLabels[c] = lbl;
            header.getChildren().add(lbl);
        }
        return header;
    }

    // ── Style computation ─────────────────────────────────────────────────

    private String computeCellStyle(int row, int col) {
        char ch = currentAlignment.getChar(row, col);
        char queryChar = currentAlignment.getChar(0, col);

        boolean isGap    = (ch == '-');
        boolean isDiff   = !isGap && row > 0 && ch != queryChar && queryChar != '-';
        boolean isRowSel = selectedRows.contains(row);
        boolean isColSel = selectedColumns.contains(col);

        String textFill;
        if (isGap) {
            textFill = "#4A5568";
        } else if (controller.getColorResiduesCheckBox().isSelected()) {
            textFill = AminoAcidColorService.colorOf(ch);   // colour by amino-acid group
        } else if (isDiff) {
            textFill = "#F0883E";
        } else {
            textFill = "#CDD9E5";
        }

        double cons = (conservationScores != null) ? conservationScores[col] : 0;

        String bg;
        if (isRowSel && isColSel)      bg = "rgba(88,166,255,0.25)";
        else if (isRowSel || isColSel) bg = "rgba(88,166,255,0.12)";
        else if (cons >= 0.95)         bg = "rgba(46,160,67,0.14)";
        else if (cons >= 0.80)         bg = "rgba(46,160,67,0.05)";
        else if (isDiff)               bg = "rgba(240,136,62,0.08)";
        else                           bg = "transparent";

        return CELL_BASE
            + "-fx-text-fill: " + textFill + "; "
            + "-fx-background-color: " + bg + ";";
    }

    private String computeNameStyle(int row) {
        boolean sel = selectedRows.contains(row);
        return NAME_BASE + (sel
            ? "-fx-text-fill: #CDD9E5; -fx-background-color: rgba(88,166,255,0.20);"
            : "-fx-text-fill: #8B949E; -fx-background-color: transparent;");
    }

    private String computeHeaderStyle(int col) {
        boolean sel = selectedColumns.contains(col);
        return HEADER_BASE + (sel
            ? "-fx-text-fill: #CDD9E5; -fx-background-color: rgba(88,166,255,0.20);"
            : "-fx-text-fill: #6B737D; -fx-background-color: transparent;");
    }

    private void refreshAllStyles() {
        if (currentAlignment == null || cellLabels == null) return;
        int rows = currentAlignment.getSequenceCount();
        int cols = currentAlignment.getAlignmentLength();

        for (int r = 0; r < rows; r++) {
            nameLabels[r].setStyle(computeNameStyle(r));
            for (int c = 0; c < cols; c++) {
                cellLabels[r][c].setStyle(computeCellStyle(r, c));
            }
        }
        for (int c = 0; c < cols; c++) {
            headerLabels[c].setStyle(computeHeaderStyle(c));
        }
        updateFoldButton();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void setStatus(String text) {
        controller.getAlignmentStatusLabel().setText(text);
    }

    private ProgressIndicator spinner;

    private void showSpinner(boolean show) {
        if (show) {
            if (spinner == null) {
                spinner = new ProgressIndicator();
                spinner.setMaxSize(14, 14);
                spinner.setStyle("-fx-progress-color: #58A6FF;");
            }
            HBox toolbar = (HBox) controller.getAlignmentStatusLabel().getParent();
            if (!toolbar.getChildren().contains(spinner)) {
                int idx = toolbar.getChildren().indexOf(
                    controller.getAlignmentStatusLabel());
                toolbar.getChildren().add(idx, spinner);
            }
        } else if (spinner != null && spinner.getParent() != null) {
            ((javafx.scene.layout.Pane) spinner.getParent())
                .getChildren().remove(spinner);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static <T> void toggle(Set<T> set, T value) {
        if (!set.remove(value)) set.add(value);
    }
}
