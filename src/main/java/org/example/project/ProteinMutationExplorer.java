package org.example.project;

import javafx.application.Application;
import javafx.stage.Stage;
import org.example.project.window.WindowView;

import java.io.IOException;

public final class ProteinMutationExplorer extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("Protein Mutation Explorer");
        stage.setScene(new WindowView().createScene());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
