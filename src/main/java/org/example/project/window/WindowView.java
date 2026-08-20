package org.example.project.window;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import org.example.project.alignment.AlignmentPresenter;
import org.example.project.statistics.StatisticsPresenter;
import org.example.project.conformationpanel.ConformationPanelPresenter;
import org.example.project.model.*;
import org.example.project.protein3d.Protein3DPresenter;
import org.example.project.sequenceeditor.SequenceEditorPresenter;

import java.io.IOException;

/**
 * Loads the Window FXML, creates all shared models, and wires up the sub-presenters.
 * This is the application's single composition root.
 */
public final class WindowView {

    private final Parent root;

    public WindowView() throws IOException {
        FXMLLoader loader = new FXMLLoader(
            WindowView.class.getResource("/project/window/Window.fxml"));
        this.root = loader.load();
        WindowController controller = loader.getController();

        // Shared models - created here and injected into presenters
        ConformationStore      conformationStore = new ConformationStore();
        ResidueSelectionModel  selectionModel    = new ResidueSelectionModel();
        UndoManager            undoManager       = new UndoManager();

        // Build presenters bottom-up
        StatisticsPresenter statisticsPresenter = new StatisticsPresenter(
            controller.getStatisticsController()
        );

        AlignmentPresenter alignmentPresenter = new AlignmentPresenter(
            controller.getAlignmentController(),
            selectionModel
        );
        alignmentPresenter.setStatisticsPresenter(statisticsPresenter);
        // foldCallback wired below after seqPresenter is constructed

        Protein3DPresenter protein3DPresenter = new Protein3DPresenter(
            controller.getProtein3DController().getSubSceneContainer(),
            controller.getProtein3DController().getAtomRadiusSlider(),
            controller.getProtein3DController().getBondRadiusSlider(),
            controller.getProtein3DController().getAtomsVisibleCheckBox(),
            controller.getProtein3DController().getBondsVisibleCheckBox(),
            controller.getProtein3DController().getRibbonVisibleCheckBox(),
            controller.getProtein3DController().getWireframeCheckBox(),
            controller.getProtein3DController().getZoomInButton(),
            controller.getProtein3DController().getZoomOutButton(),
            controller.getProtein3DController().getStructureInfoLabel(),
            selectionModel
        );

        // The statistics panel's atom-element pie follows whatever structure the 3D viewer shows
        protein3DPresenter.setStructureListener(statisticsPresenter::showStructure);

        // SequenceEditorPresenter must be created before ConformationPanelPresenter
        // so its loadConformation() method can be passed as a callback.
        SequenceEditorPresenter seqPresenter = new SequenceEditorPresenter(
            controller.getSequenceEditorController(),
            conformationStore,
            selectionModel,
            undoManager,
            protein3DPresenter,
            alignmentPresenter
        );

        // AI-edit button now lives in the 3D viewer; description + "Add" in the conformation panel
        seqPresenter.attachExternalControls(
            controller.getProtein3DController().getAiEditButton(),
            controller.getConformationPanelController().getAddConformationButton(),
            controller.getConformationPanelController().getDescriptionField()
        );

        // Req 9: fold a sequence selected in the alignment and save as conformation
        alignmentPresenter.setFoldCallback(seqPresenter::foldAndAddConformation);

        new ConformationPanelPresenter(
            controller.getConformationPanelController().getConformationFlowPane(),
            conformationStore,
            seqPresenter::loadConformation   // restores both sequence and 3D structure
        );

        new WindowPresenter(controller, undoManager);
    }

    public Scene createScene() {
        Scene scene = new Scene(root, 1500, 900);
        scene.setFill(Color.web("#0D1117"));
        scene.getStylesheets().add(
            WindowView.class.getResource("/project/style.css").toExternalForm());
        return scene;
    }
}
