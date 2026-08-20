package org.example.project.nledit;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import org.example.project.model.ai.ValidationResult;

/**
 * FXML controller for the natural-language editing dialog: only {@code @FXML}
 * fields and their getters. All behaviour lives in {@link NlEditPresenter}.
 */
public final class NlEditController {

    @FXML private TextArea requestArea;
    @FXML private Button proposeButton;
    @FXML private HBox proposeBar;
    @FXML private Label statusLabel;
    @FXML private TableView<ValidationResult> previewTable;
    @FXML private TableColumn<ValidationResult, String> statusColumn;
    @FXML private TableColumn<ValidationResult, String> editColumn;
    @FXML private TableColumn<ValidationResult, String> noteColumn;
    @FXML private Label explanationLabel;
    @FXML private Button acceptButton;
    @FXML private Button rejectButton;

    public TextArea getRequestArea()                              { return requestArea; }
    public Button getProposeButton()                             { return proposeButton; }
    public HBox getProposeBar()                                  { return proposeBar; }
    public Label getStatusLabel()                                { return statusLabel; }
    public TableView<ValidationResult> getPreviewTable()         { return previewTable; }
    public TableColumn<ValidationResult, String> getStatusColumn() { return statusColumn; }
    public TableColumn<ValidationResult, String> getEditColumn()   { return editColumn; }
    public TableColumn<ValidationResult, String> getNoteColumn()   { return noteColumn; }
    public Label getExplanationLabel()                           { return explanationLabel; }
    public Button getAcceptButton()                              { return acceptButton; }
    public Button getRejectButton()                             { return rejectButton; }
}
