package org.example.project.alignment;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

public final class AlignmentController {

    @FXML private Label alignmentStatusLabel;
    @FXML private Button foldSelectedButton;
    @FXML private CheckBox colorResiduesCheckBox;
    @FXML private ScrollPane alignmentScrollPane;
    @FXML private VBox alignmentGrid;

    public Label      getAlignmentStatusLabel()  { return alignmentStatusLabel; }
    public Button     getFoldSelectedButton()     { return foldSelectedButton; }
    public CheckBox   getColorResiduesCheckBox() { return colorResiduesCheckBox; }
    public ScrollPane getAlignmentScrollPane()   { return alignmentScrollPane; }
    public VBox       getAlignmentGrid()         { return alignmentGrid; }
}
