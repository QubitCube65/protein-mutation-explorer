package org.example.project.conformationpanel;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

public final class ConformationPanelController {

    @FXML private FlowPane  conformationFlowPane;
    @FXML private TextField descriptionField;
    @FXML private Button    addConformationButton;

    public FlowPane  getConformationFlowPane()   { return conformationFlowPane; }
    public TextField getDescriptionField()       { return descriptionField; }
    public Button    getAddConformationButton()  { return addConformationButton; }
}
