package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.OrphanGroup;
import it.infog.psiche.ui.common.DateFormats;
import it.infog.psiche.ui.common.DetachedDialog;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.FileLabels;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static it.infog.psiche.ui.common.Controls.button;

/**
 * Detached notes: the groups of notes left without a chapter, the most recently deleted first, each with the chapter it
 * belonged to, why and when it was left, a small card for each note, and the buttons to put it back on a chapter's
 * board, to put it back on the book's own board, or to delete it for good. The book's own notes read as "Lorem (book
 * notes)". Deleting for good is asked first: notes are not in Git, so nothing brings them back.
 * The dialog stays open, the groups shown again after each change, and greyed out while it is made; see
 * {@link DetachedDialog}.
 */
final class DetachedNotesDialog {

    private final DetachedDialog dialog;
    private final List<String> chapters;
    private final String bookTitle;
    private BiConsumer<OrphanGroup, String> restore = (group, chapter) -> {};
    private Consumer<OrphanGroup> delete = group -> {};

    /**
     * @param chapters  the book's chapters, by name, which notes can be restored to, besides the book's own board
     * @param bookTitle the book's title, which its own notes are named after
     */
    DetachedNotesDialog(Window owner, List<String> chapters, String bookTitle) {

        this.chapters = chapters;
        this.bookTitle = bookTitle;
        dialog = new DetachedDialog(owner, "Detached notes",
                "Notes are not in Git: what is deleted from here is gone for good.", "No detached notes.");
    }

    /**
     * Runs {@code restore} to put a group's notes on the board of the chapter named; the groups left are then handed
     * to {@link #showGroups}.
     */
    void setOnRestore(BiConsumer<OrphanGroup, String> restore) {

        this.restore = restore;
    }

    /**
     * Runs {@code delete} to delete a group's notes for good, once the author has confirmed it; the groups left are
     * then handed to {@link #showGroups}.
     */
    void setOnDelete(Consumer<OrphanGroup> delete) {

        this.delete = delete;
    }

    /** Shows {@code groups} and waits until the author closes the dialog. */
    void showAndWait(List<OrphanGroup> groups) {

        showGroups(groups);
        dialog.showAndWait();
    }

    /** Shows {@code groups} in place of those shown, and lets the author act on them again. */
    void showGroups(List<OrphanGroup> groups) {

        dialog.setGroups(groups.stream().map(group -> (Node) groupView(group)).toList());
    }

    void close() {

        dialog.close();
    }

    /**
     * Asks, as a deleted chapter is restored, whether the notes it had go back with it.
     *
     * @return true for Restore notes, the default
     */
    static boolean askRestore(Window owner, OrphanGroup group) {

        ButtonType chapterOnly = new ButtonType("Chapter only", ButtonData.CANCEL_CLOSE);
        ButtonType restoreNotes = new ButtonType("Restore notes", ButtonData.OK_DONE);
        String when = group.deletedAt()
                .map(at -> ", on %s at %s".formatted(DateFormats.DAY.format(at), DateFormats.TIME.format(at)))
                .orElse("");

        return Dialogs.choose(owner, "Restore its notes too?",
                        "%s had %s when it was deleted%s.".formatted(group.chapter(), count(group), when),
                        chapterOnly, restoreNotes)
                .filter(restoreNotes::equals)
                .isPresent();
    }

    // The groups

    /** The chapter above the cause and date, the buttons beside them, and below them a small card for each note. */
    private VBox groupView(OrphanGroup group) {

        String when = group.deletedAt().map(at -> " · " + DateFormats.DAY_AND_TIME.format(at)).orElse("");
        return DetachedDialog.group(boardName(group.chapter(), bookTitle), group.cause().label() + when,
                group.notes().stream().map(note -> (Node) card(note)).toList(),
                button("Restore to chapter", () -> restore(group)),
                button("Restore to book", () -> restoreToBook(group)),
                button("Delete permanently", () -> delete(group)));
    }

    /** A note as a small card in its colour: its text, and the name of its file under it. */
    private static VBox card(Note note) {

        Label text = new Label(note.text());
        text.getStyleClass().add("deleted-note-text");
        text.setWrapText(true);
        VBox.setVgrow(text, Priority.ALWAYS);

        VBox card = new VBox(text);
        card.getStyleClass().addAll("deleted-note", "note-" + note.layout().color().key());
        note.attachment().ifPresent(file -> {
            Label name = new Label(FileLabels.shortened(file.getFileName().toString()));
            name.getStyleClass().add("deleted-note-file");
            card.getChildren().add(name);
        });
        return card;
    }

    // Restoring and deleting

    /** Asks which chapter's board the notes go to, their own chapter first if the book still has it. */
    private void restore(OrphanGroup group) {

        if (chapters.isEmpty()) {
            Dialogs.inform(window(), "No chapter to restore to",
                    "Create a chapter first, then restore the notes to it, or restore them to the book.");
            return;
        }

        String suggested = chapters.contains(group.chapter()) ? group.chapter() : chapters.getFirst();
        Optional<String> chapter = Dialogs.chooseChapter(window(),
                "Restore %s to a chapter".formatted(count(group)), chapters, suggested, "Restore");
        if (chapter.isEmpty())
            return;

        dialog.setBusy();
        restore.accept(group, chapter.get());
    }

    /** Puts the notes back on the book's own board, beside the notes it has; nothing is asked, as nothing is lost. */
    private void restoreToBook(OrphanGroup group) {

        dialog.setBusy();
        restore.accept(group, NoteStore.BOOK);
    }

    private void delete(OrphanGroup group) {

        ButtonType deleteButton = new ButtonType("Delete", ButtonData.OK_DONE);
        Optional<ButtonType> choice = Dialogs.choose(window(), "Delete %s for good?".formatted(count(group)),
                notInGit(group), ButtonType.CANCEL, deleteButton);
        if (choice.filter(deleteButton::equals).isEmpty())
            return;

        dialog.setBusy();
        delete.accept(group);
    }

    /** "The notes of 03-sera and their files are not in Git, so they can't be recovered.", as many and as filed. */
    private String notInGit(OrphanGroup group) {

        String name = boardName(group.chapter(), bookTitle);
        boolean files = group.notes().stream().anyMatch(note -> note.attachment().isPresent());
        if (group.notes().size() == 1)
            return (files ? "The note of %s and its file are" : "The note of %s is").formatted(name)
                    + " not in Git, so it can't be recovered.";

        return (files ? "The notes of %s and their files are" : "The notes of %s are").formatted(name)
                + " not in Git, so they can't be recovered.";
    }

    private Window window() {

        return dialog.window();
    }

    /** The name the author knows the board kept in {@code folder} by: the chapter's, or "Lorem (book notes)". */
    static String boardName(String folder, String bookTitle) {

        return folder.equals(NoteStore.BOOK) ? bookTitle + " (book notes)" : folder;
    }

    /** "1 note", or "3 notes". */
    static String count(OrphanGroup group) {

        int notes = group.notes().size();
        return notes == 1 ? "1 note" : notes + " notes";
    }
}
