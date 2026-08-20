package org.example.project.sequenceeditor;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

public final class SequenceEditorController {

    @FXML private Button loadFastaButton;
    @FXML private Button saveFastaButton;
    @FXML private Button undoButton;
    @FXML private Button redoButton;
    @FXML private ComboBox<Character> mutationComboBox;
    @FXML private Button applyMutationButton;
    @FXML private Button insertButton;
    @FXML private Button deleteButton;
    @FXML private Button esmFoldButton;
    @FXML private ComboBox<Integer> blastHitsComboBox;
    @FXML private Button findRelatedButton;
    @FXML private Label statusLabel;
    @FXML private HBox sequenceBox;

    public Button          getLoadFastaButton()       { return loadFastaButton; }
    public Button          getSaveFastaButton()       { return saveFastaButton; }
    public Button          getUndoButton()            { return undoButton; }
    public Button          getRedoButton()            { return redoButton; }
    public ComboBox<Character> getMutationComboBox()  { return mutationComboBox; }
    public Button          getApplyMutationButton()   { return applyMutationButton; }
    public Button          getInsertButton()          { return insertButton; }
    public Button          getDeleteButton()          { return deleteButton; }
    public Button          getEsmFoldButton()          { return esmFoldButton; }
    public ComboBox<Integer> getBlastHitsComboBox()   { return blastHitsComboBox; }
    public Button          getFindRelatedButton()     { return findRelatedButton; }
    public Label           getStatusLabel()           { return statusLabel; }
    public HBox            getSequenceBox()           { return sequenceBox; }
}
