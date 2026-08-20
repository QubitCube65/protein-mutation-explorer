package org.example.project.protein3d;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

public final class Protein3DController {

    @FXML private StackPane subSceneContainer;
    @FXML private Slider    atomRadiusSlider;
    @FXML private Slider    bondRadiusSlider;
    @FXML private CheckBox  atomsVisibleCheckBox;
    @FXML private CheckBox  bondsVisibleCheckBox;
    @FXML private CheckBox  ribbonVisibleCheckBox;
    @FXML private CheckBox  wireframeCheckBox;
    @FXML private Button    zoomInButton;
    @FXML private Button    zoomOutButton;
    @FXML private Label     structureInfoLabel;
    @FXML private Button    aiEditButton;

    public StackPane getSubSceneContainer()    { return subSceneContainer; }
    public Slider    getAtomRadiusSlider()     { return atomRadiusSlider; }
    public Slider    getBondRadiusSlider()     { return bondRadiusSlider; }
    public CheckBox  getAtomsVisibleCheckBox() { return atomsVisibleCheckBox; }
    public CheckBox  getBondsVisibleCheckBox() { return bondsVisibleCheckBox; }
    public CheckBox  getRibbonVisibleCheckBox(){ return ribbonVisibleCheckBox; }
    public CheckBox  getWireframeCheckBox()    { return wireframeCheckBox; }
    public Button    getZoomInButton()         { return zoomInButton; }
    public Button    getZoomOutButton()        { return zoomOutButton; }
    public Label     getStructureInfoLabel()   { return structureInfoLabel; }
    public Button    getAiEditButton()         { return aiEditButton; }
}
