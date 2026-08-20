package org.example.project.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.ArrayDeque;
import java.util.Deque;

/** Command-pattern undo/redo manager for sequence editing operations. */
public final class UndoManager {

    /** A reversible edit - knows how to undo and redo itself, and names itself. */
    public interface Edit {
        void undo();
        void redo();
        /** Short human-readable name shown in the Undo/Redo UI (e.g. "Mutation"). */
        default String label() { return ""; }
    }

    private final Deque<Edit> undoStack = new ArrayDeque<>();
    private final Deque<Edit> redoStack = new ArrayDeque<>();

    private final BooleanProperty canUndo = new SimpleBooleanProperty(false);
    private final BooleanProperty canRedo = new SimpleBooleanProperty(false);

    // Full menu strings, e.g. "Undo Mutation" / "Redo" - bound to the Edit menu items.
    private final ReadOnlyStringWrapper undoText = new ReadOnlyStringWrapper("Undo");
    private final ReadOnlyStringWrapper redoText = new ReadOnlyStringWrapper("Redo");

    /** Records an already-executed edit and clears the redo stack. */
    public void push(Edit edit) {
        undoStack.push(edit);
        redoStack.clear();
        sync();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        Edit e = undoStack.pop();
        e.undo();
        redoStack.push(e);
        sync();
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        Edit e = redoStack.pop();
        e.redo();
        undoStack.push(e);
        sync();
    }

    /** Clears all history (call when a completely new sequence is loaded). */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        sync();
    }

    public BooleanProperty canUndoProperty() { return canUndo; }
    public BooleanProperty canRedoProperty() { return canRedo; }

    /** "Undo &lt;label&gt;" for the top of the undo stack, or just "Undo" when empty. */
    public ReadOnlyStringProperty undoTextProperty() { return undoText.getReadOnlyProperty(); }
    /** "Redo &lt;label&gt;" for the top of the redo stack, or just "Redo" when empty. */
    public ReadOnlyStringProperty redoTextProperty() { return redoText.getReadOnlyProperty(); }

    private void sync() {
        canUndo.set(!undoStack.isEmpty());
        canRedo.set(!redoStack.isEmpty());
        undoText.set(menuText("Undo", undoStack));
        redoText.set(menuText("Redo", redoStack));
    }

    private static String menuText(String verb, Deque<Edit> stack) {
        if (stack.isEmpty()) return verb;
        String label = stack.peek().label();
        return (label == null || label.isBlank()) ? verb : verb + " " + label;
    }
}
