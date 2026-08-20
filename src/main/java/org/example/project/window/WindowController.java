package org.example.project.window;

import javafx.fxml.FXML;
import javafx.scene.control.MenuItem;
import org.example.project.alignment.AlignmentController;
import org.example.project.statistics.StatisticsController;
import org.example.project.conformationpanel.ConformationPanelController;
import org.example.project.protein3d.Protein3DController;
import org.example.project.sequenceeditor.SequenceEditorController;

public final class WindowController {

    @FXML private MenuItem undoMenuItem;
    @FXML private MenuItem redoMenuItem;
    @FXML private MenuItem quitMenuItem;
    @FXML private MenuItem helpMenuItem;

    // Sub-controllers injected by FXMLLoader via fx:include
    @FXML private SequenceEditorController    sequenceEditorController;
    @FXML private AlignmentController         alignmentController;
    @FXML private StatisticsController        statisticsController;
    @FXML private Protein3DController         protein3DController;
    @FXML private ConformationPanelController conformationPanelController;

    public MenuItem getUndoMenuItem()   { return undoMenuItem; }
    public MenuItem getRedoMenuItem()   { return redoMenuItem; }
    public MenuItem getQuitMenuItem()   { return quitMenuItem; }
    public MenuItem getHelpMenuItem()   { return helpMenuItem; }

    public SequenceEditorController    getSequenceEditorController()    { return sequenceEditorController; }
    public AlignmentController         getAlignmentController()         { return alignmentController; }
    public StatisticsController        getStatisticsController()        { return statisticsController; }
    public Protein3DController         getProtein3DController()         { return protein3DController; }
    public ConformationPanelController getConformationPanelController() { return conformationPanelController; }
}
