package org.example.project.statistics;

import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;

public final class StatisticsController {

    @FXML private Label seqCountLabel;
    @FXML private Label alignLengthLabel;
    @FXML private Label activeSeqLabel;
    @FXML private BarChart<String, Number> aaChart;
    @FXML private CategoryAxis aaAxis;
    @FXML private CheckBox aaPieCheckBox;
    @FXML private CheckBox atomPieCheckBox;
    @FXML private PieChart aaPieChart;
    @FXML private PieChart atomPieChart;

    public Label    getSeqCountLabel()    { return seqCountLabel; }
    public Label    getAlignLengthLabel() { return alignLengthLabel; }
    public Label    getActiveSeqLabel()   { return activeSeqLabel; }
    public BarChart<String, Number> getAaChart() { return aaChart; }
    public CategoryAxis getAaAxis()       { return aaAxis; }
    public CheckBox getAaPieCheckBox()    { return aaPieCheckBox; }
    public CheckBox getAtomPieCheckBox()  { return atomPieCheckBox; }
    public PieChart getAaPieChart()       { return aaPieChart; }
    public PieChart getAtomPieChart()     { return atomPieChart; }
}
