package org.example.project.statistics;

import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.collections.FXCollections;
import org.example.project.model.AlignmentResult;
import org.example.project.model.AminoAcidColorService;
import org.example.project.model.Atom;
import org.example.project.model.ProteinStructure;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StatisticsPresenter {

    private static final String AA_ORDER = "ACDEFGHIKLMNPQRSTVWY";
    private static final String[] GROUP_ORDER = {
        "Hydrophobic", "Aromatic", "Positive", "Negative", "Polar", "Cysteine", "Special", "Other"
    };

    private final StatisticsController controller;

    // Cached data so the pies can be (re)built when their checkbox is toggled
    private AlignmentResult currentResult;
    private int currentRow = -1;
    private ProteinStructure currentStructure;

    public StatisticsPresenter(StatisticsController controller) {
        this.controller = controller;

        controller.getAaPieCheckBox().selectedProperty().addListener((o, ov, nv) -> {
            setChartShown(controller.getAaPieChart(), nv);
            if (nv) buildAaGroupPie();
        });
        controller.getAtomPieCheckBox().selectedProperty().addListener((o, ov, nv) -> {
            setChartShown(controller.getAtomPieChart(), nv);
            if (nv) buildAtomPie();
        });
    }

    // ── Public API ────────────────────────────────────────────────────────

    public void showAlignment(AlignmentResult result) {
        controller.getSeqCountLabel()
            .setText("Sequences: " + result.getSequenceCount());
        controller.getAlignLengthLabel()
            .setText("Alignment length: " + result.getAlignmentLength());
        showRow(result, 0);
    }

    public void showRow(AlignmentResult result, int row) {
        this.currentResult = result;
        this.currentRow = row;
        String name = result.getName(row);
        controller.getActiveSeqLabel()
            .setText("Showing: " + (name.length() > 30 ? name.substring(0, 29) + "…" : name));
        buildChart(result, row);
        if (controller.getAaPieCheckBox().isSelected()) buildAaGroupPie();
    }

    /** Called when the 3D viewer shows (or clears) a structure - drives the atom pie. */
    public void showStructure(ProteinStructure structure) {
        this.currentStructure = structure;
        if (controller.getAtomPieCheckBox().isSelected()) buildAtomPie();
    }

    public void clear() {
        controller.getSeqCountLabel().setText("Sequences: -");
        controller.getAlignLengthLabel().setText("Alignment length: -");
        controller.getActiveSeqLabel().setText("Showing: -");
        controller.getAaChart().getData().clear();
        controller.getAaPieChart().getData().clear();
        currentResult = null;
        currentRow = -1;
        // note: currentStructure is left alone - it reflects the 3D viewer, not the alignment
    }

    // ── Bar chart (AA composition) ──────────────────────────────────────────

    private void buildChart(AlignmentResult result, int row) {
        String seq = result.getAlignedSequence(row).replace("-", "");

        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (char aa : AA_ORDER.toCharArray()) counts.put(aa, 0);
        for (char c : seq.toCharArray()) counts.computeIfPresent(c, (k, v) -> v + 1);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        counts.forEach((aa, cnt) ->
            series.getData().add(new XYChart.Data<>(String.valueOf(aa), cnt)));

        controller.getAaChart().getData().setAll(series);

        // Colour each bar by amino-acid group, matching the alignment view's palette
        for (XYChart.Data<String, Number> d : series.getData()) {
            String color = AminoAcidColorService.colorOf(d.getXValue().charAt(0));
            styleNode(d.getNode(), d.nodeProperty(), "-fx-bar-fill: " + color + ";");
        }

        // Force integer-only Y-axis ticks (counts are always whole numbers)
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        NumberAxis yAxis = (NumberAxis) controller.getAaChart().getYAxis();
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(max + 1);
        yAxis.setTickUnit(Math.max(1, (int) Math.ceil(max / 6.0)));
    }

    // ── Pie: amino-acid groups ──────────────────────────────────────────────

    private void buildAaGroupPie() {
        PieChart pie = controller.getAaPieChart();
        if (currentResult == null || currentRow < 0) {
            pie.getData().clear();
            return;
        }
        String seq = currentResult.getAlignedSequence(currentRow).replace("-", "");

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String g : GROUP_ORDER) counts.put(g, 0);
        for (char c : seq.toCharArray()) {
            counts.merge(AminoAcidColorService.groupOf(c), 1, Integer::sum);
        }

        var data = FXCollections.<PieChart.Data>observableArrayList();
        counts.forEach((group, cnt) -> {
            if (cnt > 0) data.add(new PieChart.Data(group + " (" + cnt + ")", cnt));
        });
        pie.setData(data);

        // Colour each slice by its group colour
        for (PieChart.Data d : data) {
            String group = d.getName().replaceAll(" \\(\\d+\\)$", "");
            styleNode(d.getNode(), d.nodeProperty(),
                "-fx-pie-color: " + AminoAcidColorService.groupColor(group) + ";");
        }
    }

    // ── Pie: atom elements ──────────────────────────────────────────────────

    private void buildAtomPie() {
        PieChart pie = controller.getAtomPieChart();
        if (currentStructure == null || currentStructure.getAtomCount() == 0) {
            pie.getData().clear();
            return;
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Atom a : currentStructure.getAtoms()) {
            String el = a.getElement() == null ? "?" : a.getElement().trim().toUpperCase();
            counts.merge(el, 1, Integer::sum);
        }

        var data = FXCollections.<PieChart.Data>observableArrayList();
        counts.forEach((el, cnt) -> data.add(new PieChart.Data(el + " (" + cnt + ")", cnt)));
        pie.setData(data);

        for (PieChart.Data d : data) {
            String el = d.getName().replaceAll(" \\(\\d+\\)$", "");
            styleNode(d.getNode(), d.nodeProperty(), "-fx-pie-color: " + elementColor(el) + ";");
        }
    }

    /** Standard CPK-style colours for the common protein elements. */
    private static String elementColor(String element) {
        return switch (element) {
            case "C"  -> "#909090";
            case "N"  -> "#3050F8";
            case "O"  -> "#FF0D0D";
            case "S"  -> "#FFE030";
            case "H"  -> "#E6E6E6";
            case "P"  -> "#FF8000";
            default   -> "#BFC2C7";
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void setChartShown(Node chart, boolean shown) {
        chart.setVisible(shown);
        chart.setManaged(shown);
    }

    /** Applies a style to a chart node now, or as soon as the node is created. */
    private static void styleNode(Node node,
                                  javafx.beans.value.ObservableValue<? extends Node> nodeProperty,
                                  String style) {
        if (node != null) {
            node.setStyle(style);
        } else {
            nodeProperty.addListener((obs, o, n) -> {
                if (n != null) n.setStyle(style);
            });
        }
    }
}
