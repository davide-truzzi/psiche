package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.OrphanGroup;
import it.infog.psiche.ui.common.DateFormats;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.notes.card.FileLabels;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
 * board or to delete it for good. Deleting for good is asked first: notes are not in Git, so nothing brings them back.
 * The dialog stays open, the groups shown again after each change, and greyed out while it is made.
 */
final class DetachedNotesDialog {

    private static final ButtonType CLOSE = new ButtonType("Close", ButtonData.CANCEL_CLOSE);

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final VBox groupList = new VBox();
    private final List<String> chapters;
    private BiConsumer<OrphanGroup, String> restore = (group, chapter) -> {};
    private Consumer<OrphanGroup> delete = group -> {};

    /** @param chapters the book's chapters, by name, which notes can be restored to */
    DetachedNotesDialog(Window owner, List<String> chapters) {

        this.chapters = chapters;

        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText("Detached notes");
        dialog.setResizable(true);
        dialog.getDialogPane().getStyleClass().add("detached-notes-dialog");
        dialog.getDialogPane().getButtonTypes().setAll(CLOSE);

        groupList.getStyleClass().add("detached-notes-groups");
        ScrollPane scroll = new ScrollPane(groupList);
        scroll.setFitToWidth(true);

        Label hint = new Label("Notes are not in Git: what is deleted from here is gone for good.");
        hint.getStyleClass().add("history-hint");

        VBox content = new VBox(scroll, hint);
        content.getStyleClass().add("detached-notes");
        dialog.getDialogPane().setContent(content);
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

        groupList.getChildren().clear();
        groupList.setDisable(false);
        if (groups.isEmpty()) {
            Label none = new Label("No detached notes.");
            none.getStyleClass().add("detached-notes-details");
            groupList.getChildren().add(none);
            return;
        }

        for (OrphanGroup group : groups) {
            if (!groupList.getChildren().isEmpty())
                groupList.getChildren().add(rule());
            groupList.getChildren().add(groupView(group));
        }
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

    /**
     * The chapter above the cause and date, the buttons beside them, and below them a small card for each note. The
     * buttons keep their whole text; a long chapter name wraps.
     */
    private VBox groupView(OrphanGroup group) {

        Label chapter = wrapping(group.chapter(), "detached-notes-chapter");
        String when = group.deletedAt().map(at -> " · " + DateFormats.DAY_AND_TIME.format(at)).orElse("");
        Label details = wrapping(group.cause().label() + when, "detached-notes-details");

        VBox source = new VBox(chapter, details);
        source.getStyleClass().add("detached-notes-source");
        source.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(source, Priority.ALWAYS);

        Button restoreButton = button("Restore to chapter", () -> restore(group));
        restoreButton.setMinWidth(Region.USE_PREF_SIZE);
        Button deleteButton = button("Delete permanently", () -> delete(group));
        deleteButton.setMinWidth(Region.USE_PREF_SIZE);

        HBox heading = new HBox(source, restoreButton, deleteButton);
        heading.getStyleClass().add("detached-notes-heading");
        heading.setAlignment(Pos.CENTER_LEFT);

        FlowPane cards = new FlowPane();
        cards.getStyleClass().add("detached-notes-cards");
        group.notes().forEach(note -> cards.getChildren().add(card(note)));

        VBox view = new VBox(heading, cards);
        view.getStyleClass().add("detached-notes-group");
        return view;
    }

    private static Label wrapping(String text, String styleClass) {

        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        return label;
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

    private static Region rule() {

        Region rule = new Region();
        rule.getStyleClass().add("detached-notes-rule");
        return rule;
    }

    // Restoring and deleting

    /** Asks which chapter's board the notes go to, their own chapter first if the book still has it. */
    private void restore(OrphanGroup group) {

        if (chapters.isEmpty()) {
            Dialogs.inform(window(), "No chapter to restore to",
                    "Create a chapter first, then restore the notes to it.");
            return;
        }

        String suggested = chapters.contains(group.chapter()) ? group.chapter() : chapters.getFirst();
        Optional<String> chapter = Dialogs.chooseChapter(window(),
                "Restore %s to a chapter".formatted(count(group)), chapters, suggested, "Restore");
        if (chapter.isEmpty())
            return;

        groupList.setDisable(true);
        restore.accept(group, chapter.get());
    }

    private void delete(OrphanGroup group) {

        ButtonType deleteButton = new ButtonType("Delete", ButtonData.OK_DONE);
        Optional<ButtonType> choice = Dialogs.choose(window(), "Delete %s for good?".formatted(count(group)),
                notInGit(group), ButtonType.CANCEL, deleteButton);
        if (choice.filter(deleteButton::equals).isEmpty())
            return;

        groupList.setDisable(true);
        delete.accept(group);
    }

    /** "The notes of 03-sera and their files are not in Git, so they can't be recovered.", as many and as filed. */
    private static String notInGit(OrphanGroup group) {

        boolean files = group.notes().stream().anyMatch(note -> note.attachment().isPresent());
        if (group.notes().size() == 1)
            return (files ? "The note of %s and its file are" : "The note of %s is").formatted(group.chapter())
                    + " not in Git, so it can't be recovered.";

        return (files ? "The notes of %s and their files are" : "The notes of %s are").formatted(group.chapter())
                + " not in Git, so they can't be recovered.";
    }

    private Window window() {

        return dialog.getDialogPane().getScene().getWindow();
    }

    /** "1 note", or "3 notes". */
    static String count(OrphanGroup group) {

        int notes = group.notes().size();
        return notes == 1 ? "1 note" : notes + " notes";
    }
}
