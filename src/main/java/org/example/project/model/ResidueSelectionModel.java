package org.example.project.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.util.LinkedHashSet;

/** Shared selection state: the 0-based indices of selected residues. */
public final class ResidueSelectionModel {

    private final ObservableSet<Integer> selectedIndices =
            FXCollections.observableSet(new LinkedHashSet<>());

    /** The live set of selected 0-based residue indices. Observe with SetChangeListener. */
    public ObservableSet<Integer> selectedIndicesProperty() { return selectedIndices; }

    /** Replaces the selection with exactly {@code idx}, or clears it if idx &lt; 0. */
    public void setSingle(int idx) {
        selectedIndices.clear();
        if (idx >= 0) selectedIndices.add(idx);
    }

    /** Replaces the selection with the contiguous range [min(from,to)..max(from,to)]. */
    public void setRange(int from, int to) {
        selectedIndices.clear();
        int lo = Math.min(from, to), hi = Math.max(from, to);
        for (int i = lo; i <= hi; i++) selectedIndices.add(i);
    }

    /** Adds {@code idx} if not selected, removes it if already selected. */
    public void toggle(int idx) {
        if (idx < 0) return;
        if (selectedIndices.contains(idx)) selectedIndices.remove(idx);
        else selectedIndices.add(idx);
    }

    public boolean isSelected(int idx) { return selectedIndices.contains(idx); }
    public boolean isEmpty()           { return selectedIndices.isEmpty(); }
    public void clearSelection()       { selectedIndices.clear(); }

    /** Returns the lowest selected index, or -1 if nothing is selected. */
    public int getFirstSelected() {
        return selectedIndices.stream().min(Integer::compare).orElse(-1);
    }

    /** Returns the highest selected index, or -1 if nothing is selected. */
    public int getLastSelected() {
        return selectedIndices.stream().max(Integer::compare).orElse(-1);
    }
}
