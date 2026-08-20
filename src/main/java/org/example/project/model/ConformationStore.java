package org.example.project.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Observable list of all generated conformations, shared across presenters. */
public final class ConformationStore {

    private final ObservableList<Conformation> conformations =
            FXCollections.observableArrayList();

    public ObservableList<Conformation> getConformations() { return conformations; }
    public void add(Conformation c)    { conformations.add(c); }
    public void remove(Conformation c) { conformations.remove(c); }
}
