package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.FootnoteEdits;
import it.infog.psiche.markdown.Footnotes;
import it.infog.psiche.markdown.model.FootnoteMark;
import it.infog.psiche.markdown.model.FootnotePlace;
import it.infog.psiche.markdown.model.TextEdit;
import it.infog.psiche.markdown.model.enums.FootnotePart;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.OperatingSystem;

import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.IndexRange;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

import static it.infog.psiche.ui.common.Controls.menuItem;

/**
 * Footnote in the Insert submenu of the manuscript's right-click menu, and, in Default mode, where footnotes are
 * changed only from here, Modify note… and Remove note on a reference or a note, and the fixes of a wrong one: Write
 * note… and Remove reference on a reference to no note, Remove note on a note nothing refers to or a second note of a
 * label. Ctrl+Alt+F (⌘⌥⇧F on macOS) inserts a footnote at the caret: in Default mode from the {@link NoteDialog}, with
 * a note of its own or one the chapter has, in Advanced as the author types it. Each change is one edit, which a single
 * Undo reverts.
 */
final class NoteActions {

    // Ctrl+Alt+F, as in Word; on macOS ⌘⌥⇧F, as ⌘⌥F is Replace there.
    static final KeyCombination SHORTCUT = OperatingSystem.isMac()
            ? new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN,
                    KeyCombination.SHIFT_DOWN)
            : new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN);

    private final Manuscript manuscript;
    private final MenuItem insert = menuItem("Footnote", this::insert);
    private final MenuItem modify = menuItem("Modify note…", this::modify);
    private final MenuItem remove = menuItem("Remove note", this::remove);
    private final MenuItem write = menuItem("Write note…", this::write);
    private final MenuItem removeReference = menuItem("Remove reference", this::removeReference);

    NoteActions(Manuscript manuscript) {

        this.manuscript = manuscript;
        insert.setAccelerator(SHORTCUT);
    }

    /** Footnote, for the menu's Insert submenu. */
    MenuItem insertItem() {

        return insert;
    }

    /** The items on a footnote, for the menu's top level, where they show only on one. */
    List<MenuItem> modifyItems() {

        return List.of(modify, remove, write, removeReference);
    }

    /** Names, shows and enables the items for where the menu opens. */
    void update() {

        boolean editable = manuscript.isEditable();
        Optional<FootnotePlace> place = placeAtMenu();
        boolean wrongNote = place.filter(found -> found.part() == FootnotePart.NOTE && found.wrong()).isPresent();
        boolean rightOne = place.filter(found -> found.part() != FootnotePart.MISSING_REFERENCE && !found.wrong())
                .isPresent();

        insert.setText(manuscript.isDefaultMode() ? "Footnote…" : "Footnote");
        insert.setDisable(!canInsert());
        modify.setVisible(rightOne);
        remove.setVisible(rightOne || wrongNote);
        write.setVisible(place.filter(found -> found.part() == FootnotePart.MISSING_REFERENCE).isPresent());
        removeReference.setVisible(write.isVisible());
        modifyItems().forEach(item -> item.setDisable(!editable));
    }

    /** Footnote, or its shortcut: a new footnote at the end of the selection, or the caret. */
    void insert() {

        if (!canInsert())
            return;

        String text = manuscript.text();
        int position = manuscript.selection().getEnd();
        if (!manuscript.isDefaultMode()) {
            apply(FootnoteEdits.insert(text, position));
            return;
        }

        NoteDialog dialog = new NoteDialog(window(), Optional.empty(), Footnotes.numberedNotes(text));
        if (!dialog.showAndWait())
            return;

        apply(dialog.chosen()
                .map(note -> FootnoteEdits.referenced(position, note.label()))
                .orElseGet(() -> FootnoteEdits.insert(text, position, dialog.text())));
    }

    /** Modify note…: the note the menu is on, or the reference's note, changed in the dialog, or removed if emptied. */
    private void modify() {

        String text = manuscript.text();
        Optional<FootnoteMark> note = placeAtMenu()
                .flatMap(place -> Footnotes.noteOf(Footnotes.of(text), place.mark().label()));
        if (note.isEmpty())
            return;

        NoteDialog dialog = new NoteDialog(window(), Optional.of(FootnoteEdits.noteText(text, note.get())), List.of());
        if (!dialog.showAndWait())
            return;

        if (!dialog.text().isBlank())
            apply(FootnoteEdits.modified(note.get(), dialog.text()));
        else if (confirmRemoving())
            apply(FootnoteEdits.removed(text, note.get().label()));
    }

    /** Remove note: a right note with every reference to it, or a wrong one alone. */
    private void remove() {

        String text = manuscript.text();
        placeAtMenu().ifPresent(place -> apply(place.wrong()
                ? FootnoteEdits.removedNote(text, place.mark())
                : FootnoteEdits.removed(text, place.mark().label())));
    }

    /** Write note…: a note for the reference to no note the menu is on, after its paragraph. */
    private void write() {

        Optional<FootnotePlace> place = placeAtMenu();
        if (place.isEmpty())
            return;

        NoteDialog dialog = new NoteDialog(window(), Optional.empty(), List.of());
        if (dialog.showAndWait())
            apply(FootnoteEdits.added(manuscript.text(), place.get().mark(), dialog.text()));
    }

    /** Remove reference: the reference to no note the menu is on. */
    private void removeReference() {

        placeAtMenu().ifPresent(place -> apply(FootnoteEdits.removedReference(manuscript.text(), place.mark())));
    }

    /** The footnote's part the menu is on, in Default mode, where only the menu changes footnotes. */
    private Optional<FootnotePlace> placeAtMenu() {

        if (!manuscript.isDefaultMode())
            return Optional.empty();

        IndexRange spot = manuscript.menuSelection();
        return Footnotes.at(manuscript.text(), spot.getStart(), spot.getEnd());
    }

    /** True if a footnote can go at the end of the selection: never inside a block, which it would break. */
    private boolean canInsert() {

        return manuscript.isEditable() && !manuscript.inBlock(manuscript.selection().getEnd());
    }

    private Window window() {

        return manuscript.node().getScene().getWindow();
    }

    private boolean confirmRemoving() {

        ButtonType removing = new ButtonType("Remove", ButtonData.OK_DONE);
        return Dialogs.choose(window(), "Remove this note and its references?",
                        "The note is empty. Undo brings it back.", ButtonType.CANCEL, removing)
                .filter(removing::equals)
                .isPresent();
    }

    private void apply(TextEdit edit) {

        if (!manuscript.isEditable())
            return;

        manuscript.replace(edit.start(), edit.end(), edit.replacement());
        manuscript.select(edit.selectionStart(), edit.selectionEnd());
    }
}
