package org.example.project.nledit;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.example.project.model.Sequence;
import org.example.project.model.ai.SequenceEdit;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Loads {@code NlEdit.fxml}, builds the modal dialog stage and wires up its
 * {@link NlEditPresenter}. Kept separate from the presenter so the presenter
 * holds behaviour only, mirroring the {@code WindowView} composition-root style.
 */
public final class NlEditView {

    private final Stage stage;

    /**
     * @param owner            the window this dialog is modal to (may be {@code null})
     * @param currentSequence  the sequence to edit (shown to the model as context)
     * @param onAccept         receives the validated, user-approved edits and the
     *                         original request text when the user presses
     *                         "Apply accepted edits"
     */
    public NlEditView(Window owner,
                      Sequence currentSequence,
                      BiConsumer<List<SequenceEdit>, String> onAccept) throws IOException {
        FXMLLoader loader = new FXMLLoader(
            NlEditView.class.getResource("/project/nledit/NlEdit.fxml"));
        Parent root = loader.load();
        NlEditController controller = loader.getController();

        stage = new Stage();
        stage.setTitle("AI Sequence Editing");
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) stage.initOwner(owner);

        Scene scene = new Scene(root, 660, 560);
        scene.setFill(Color.web("#0D1117"));
        scene.getStylesheets().add(
            NlEditView.class.getResource("/project/style.css").toExternalForm());
        stage.setScene(scene);

        new NlEditPresenter(controller, currentSequence, onAccept, stage);
    }

    /** Shows the dialog (non-blocking; modality still blocks the owner window). */
    public void show() {
        stage.show();
    }
}
