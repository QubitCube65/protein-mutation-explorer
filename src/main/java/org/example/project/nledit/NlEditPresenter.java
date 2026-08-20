package org.example.project.nledit;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.example.project.model.Sequence;
import org.example.project.model.ai.EditParseException;
import org.example.project.model.ai.EditParser;
import org.example.project.model.ai.EditPromptBuilder;
import org.example.project.model.ai.EditProposal;
import org.example.project.model.ai.EditValidator;
import org.example.project.model.ai.OpenAIService;
import org.example.project.model.ai.ProxyKeyProvider;
import org.example.project.model.ai.SequenceEdit;
import org.example.project.model.ai.ValidationResult;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Drives the natural-language editing dialog:
 * <ol>
 *   <li>Sends the current sequence and the user's request to the OpenAI proxy
 *       (on a background thread).</li>
 *   <li>Parses the JSON response into edits and validates each one.</li>
 *   <li>Shows a preview table where every edit is marked valid (✓) or rejected (✗).</li>
 *   <li>On "Apply accepted edits", hands the valid edits to the {@code onAccept}
 *       callback and closes; on "Cancel", closes without changing anything.</li>
 * </ol>
 *
 * <p>The presenter never mutates the sequence itself - it only proposes. Applying
 * the accepted edits, re-folding and storing the conformation is the caller's job.
 */
public final class NlEditPresenter {

    private final NlEditController controller;
    private final Sequence currentSequence;
    private final BiConsumer<List<SequenceEdit>, String> onAccept;
    private final Stage stage;

    private final OpenAIService openAIService = new OpenAIService();
    private ProgressIndicator spinner;

    private List<ValidationResult> lastResults = List.of();

    public NlEditPresenter(NlEditController controller,
                           Sequence currentSequence,
                           BiConsumer<List<SequenceEdit>, String> onAccept,
                           Stage stage) {
        this.controller      = controller;
        this.currentSequence = currentSequence;
        this.onAccept        = onAccept;
        this.stage           = stage;

        setupTable();

        controller.getProposeButton().setOnAction(e -> onPropose());
        controller.getAcceptButton() .setOnAction(e -> onAcceptClicked());
        controller.getRejectButton() .setOnAction(e -> stage.close());
    }

    // ── Table wiring ───────────────────────────────────────────────────────

    private void setupTable() {
        // Let the three columns share the full table width (no empty gap on the right)
        controller.getPreviewTable().setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        controller.getStatusColumn().setCellValueFactory(
            cd -> new ReadOnlyStringWrapper(cd.getValue().valid() ? "✓" : "✗"));
        controller.getEditColumn().setCellValueFactory(
            cd -> new ReadOnlyStringWrapper(cd.getValue().editDescription()));
        controller.getNoteColumn().setCellValueFactory(
            cd -> new ReadOnlyStringWrapper(cd.getValue().message()));

        // Wrap the Note text so the full reason is readable (no truncation)
        controller.getNoteColumn().setCellFactory(col -> {
            TableCell<ValidationResult, String> cell = new TableCell<>();
            Text text = new Text();
            text.setStyle("-fx-fill: #8B949E;");
            text.wrappingWidthProperty().bind(col.widthProperty().subtract(12));
            text.textProperty().bind(cell.itemProperty());
            cell.setGraphic(text);
            return cell;
        });

        // Colour the status cell green (valid) or red (rejected)
        controller.getStatusColumn().setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-alignment: center; -fx-font-weight: bold; -fx-text-fill: "
                        + ("✓".equals(item) ? "#3FB950" : "#F85149") + ";");
                }
            }
        });
    }

    // ── Propose ────────────────────────────────────────────────────────────

    private void onPropose() {
        String request = controller.getRequestArea().getText();
        if (request == null || request.isBlank()) {
            setStatus("Enter a request first.");
            return;
        }
        if (currentSequence == null) {
            setStatus("No sequence loaded.");
            return;
        }
        if (!ProxyKeyProvider.isConfigured()) {
            showError("No API key configured",
                "No OpenAI proxy key was found.\n\n"
                + "Set the environment variable '" + ProxyKeyProvider.ENV_VAR
                + "' or paste your key into the file '" + ProxyKeyProvider.KEY_FILE
                + "' in the project root, then try again.");
            return;
        }

        controller.getProposeButton().setDisable(true);
        controller.getAcceptButton().setDisable(true);
        controller.getPreviewTable().getItems().clear();
        controller.getExplanationLabel().setText("");
        showSpinner(true);
        setStatus("Asking the model…");

        String system = EditPromptBuilder.systemPrompt();
        String user   = EditPromptBuilder.userPrompt(currentSequence, request);

        Task<PreviewData> task = new Task<>() {
            @Override protected PreviewData call() throws Exception {
                String content = openAIService.complete(system, user);
                EditProposal proposal = EditParser.parse(content);
                List<ValidationResult> results =
                    EditValidator.validate(currentSequence, proposal.edits());
                return new PreviewData(proposal, results);
            }
        };

        task.setOnSucceeded(e -> {
            showSpinner(false);
            controller.getProposeButton().setDisable(false);
            PreviewData data = task.getValue();
            showProposal(data);
        });

        task.setOnFailed(e -> {
            showSpinner(false);
            controller.getProposeButton().setDisable(false);
            Throwable ex = task.getException();
            String detail = ex instanceof EditParseException
                ? "The AI returned a response we couldn't parse:\n" + ex.getMessage()
                : (ex != null ? ex.getMessage() : "Unknown error");
            setStatus("Request failed.");
            showError("AI request failed", detail);
        });

        Thread t = new Thread(task, "openai-thread");
        t.setDaemon(true);
        t.start();
    }

    private void showProposal(PreviewData data) {
        List<ValidationResult> results = data.results();
        lastResults = results;
        controller.getPreviewTable().setItems(FXCollections.observableArrayList(results));

        String explanation = data.proposal().explanation();
        controller.getExplanationLabel().setText(
            explanation == null || explanation.isBlank() ? "" : "Model: " + explanation);

        long valid = results.stream().filter(ValidationResult::valid).count();
        long rejected = results.size() - valid;
        boolean canApply = valid > 0;
        controller.getAcceptButton().setDisable(!canApply);

        if (results.isEmpty()) {
            setStatus("The model proposed no edits.");
        } else {
            setStatus(valid + " valid, " + rejected + " rejected"
                + (canApply ? "" : " - nothing to apply"));
        }
    }

    // ── Accept ─────────────────────────────────────────────────────────────

    private void onAcceptClicked() {
        List<SequenceEdit> accepted = lastResults.stream()
            .filter(ValidationResult::valid)
            .map(ValidationResult::edit)
            .collect(Collectors.toList());
        if (accepted.isEmpty()) return;
        String request = controller.getRequestArea().getText();
        stage.close();
        onAccept.accept(accepted, request == null ? "" : request.trim());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void showSpinner(boolean show) {
        if (show) {
            if (spinner == null) {
                spinner = new ProgressIndicator();
                spinner.setMaxSize(16, 16);
                spinner.setStyle("-fx-progress-color: #58A6FF;");
            }
            if (!controller.getProposeBar().getChildren().contains(spinner)) {
                controller.getProposeBar().getChildren().add(1, spinner);
            }
        } else if (spinner != null) {
            controller.getProposeBar().getChildren().remove(spinner);
        }
    }

    private void setStatus(String text) {
        controller.getStatusLabel().setText(text);
    }

    private void showError(String header, String content) {
        Runnable show = () -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
            alert.setTitle("AI Editing");
            alert.setHeaderText(header);
            alert.initOwner(stage);
            alert.showAndWait();
        };
        if (Platform.isFxApplicationThread()) show.run();
        else Platform.runLater(show);
    }

    /** Bundles one AI round-trip: the raw proposal plus its validation results. */
    private record PreviewData(EditProposal proposal, List<ValidationResult> results) {}
}
