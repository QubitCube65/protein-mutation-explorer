package org.example.project.conformationpanel;

import javafx.collections.ListChangeListener;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.example.project.model.Conformation;
import org.example.project.model.ConformationStore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Listens to the ConformationStore and keeps the FlowPane in sync.
 * Clicking a card invokes the onSelect callback, which restores both the
 * sequence and the 3D structure in the main editor.
 */
public final class ConformationPanelPresenter {

    private static final String CARD_NORMAL =
        "-fx-background-color: " +
            "linear-gradient(to bottom, rgba(255,255,255,0.08), rgba(255,255,255,0.04)); " +
        "-fx-border-color: rgba(255,255,255,0.14); -fx-border-radius: 8; " +
        "-fx-background-radius: 8; -fx-padding: 10; -fx-cursor: hand; " +
        "-fx-min-width: 140; -fx-max-width: 160; " +
        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.40), 8, 0, 0, 2);";
    private static final String CARD_HOVER =
        "-fx-background-color: " +
            "linear-gradient(to bottom, rgba(88,166,255,0.18), rgba(88,166,255,0.08)); " +
        "-fx-border-color: rgba(88,166,255,0.45); -fx-border-radius: 8; " +
        "-fx-background-radius: 8; -fx-padding: 10; -fx-cursor: hand; " +
        "-fx-min-width: 140; -fx-max-width: 160; " +
        "-fx-effect: dropshadow(gaussian, rgba(88,166,255,0.25), 10, 0, 0, 0);";
    private static final String CARD_SELECTED =
        "-fx-background-color: " +
            "linear-gradient(to bottom, rgba(88,166,255,0.28), rgba(88,166,255,0.14)); " +
        "-fx-border-color: #58A6FF; -fx-border-width: 1.5; -fx-border-radius: 8; " +
        "-fx-background-radius: 8; -fx-padding: 10; -fx-cursor: hand; " +
        "-fx-min-width: 140; -fx-max-width: 160; " +
        "-fx-effect: dropshadow(gaussian, rgba(88,166,255,0.40), 12, 0, 0, 0);";

    private final FlowPane flowPane;
    private final ConformationStore conformationStore;
    private final Consumer<Conformation> onSelect;

    private VBox selectedCard = null;
    private final Map<Conformation, VBox> cardMap = new LinkedHashMap<>();

    public ConformationPanelPresenter(FlowPane flowPane,
                                      ConformationStore conformationStore,
                                      Consumer<Conformation> onSelect) {
        this.flowPane         = flowPane;
        this.conformationStore = conformationStore;
        this.onSelect         = onSelect;

        conformationStore.getConformations().addListener(
            (ListChangeListener<Conformation>) change -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        for (Conformation c : change.getAddedSubList()) {
                            VBox card = createCard(c);
                            cardMap.put(c, card);
                            flowPane.getChildren().add(card);
                        }
                    }
                    if (change.wasRemoved()) {
                        for (Conformation c : change.getRemoved()) {
                            VBox card = cardMap.remove(c);
                            if (card != null) {
                                flowPane.getChildren().remove(card);
                                if (card == selectedCard) selectedCard = null;
                            }
                        }
                    }
                }
            }
        );
    }

    private VBox createCard(Conformation conformation) {
        VBox card = new VBox(4);
        card.setStyle(CARD_NORMAL);

        int index = conformationStore.getConformations().indexOf(conformation) + 1;
        String desc = conformation.getDescription().isBlank()
            ? "Conformation " + index : conformation.getDescription();

        Label descLabel = new Label(desc);
        descLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11; -fx-text-fill: #CDD9E5;");
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(140);

        Label seqLabel = new Label(conformation.shortLabel());
        seqLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 10; -fx-text-fill: #8B949E;");

        String structInfo = conformation.getStructure() != null
            ? conformation.getSequence().length() + " aa · 3D ✓"
            : conformation.getSequence().length() + " aa · no structure";
        Label infoLabel = new Label(structInfo);
        infoLabel.setStyle("-fx-font-size: 9; -fx-text-fill: " +
            (conformation.getStructure() != null ? "#58A6FF;" : "#4A5568;"));

        card.getChildren().addAll(descLabel, seqLabel, infoLabel);

        card.setOnMouseEntered(e -> { if (card != selectedCard) card.setStyle(CARD_HOVER); });
        card.setOnMouseExited(e  -> { if (card != selectedCard) card.setStyle(CARD_NORMAL); });
        card.setOnMouseClicked(e -> {
            if (selectedCard != null) selectedCard.setStyle(CARD_NORMAL);
            selectedCard = card;
            card.setStyle(CARD_SELECTED);
            onSelect.accept(conformation);
        });

        return card;
    }
}
