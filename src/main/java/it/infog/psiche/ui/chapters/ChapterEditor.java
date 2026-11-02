package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.collections.ObservableSet;
import javafx.scene.Node;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * The manuscript editor: one chapter at a time. Switching chapters never writes to disk; unsaved text stays in memory
 * as a draft, is restored when the author returns to that chapter, and is written only by {@link #save()} or
 * {@link #saveAll()}. The page the author writes on is the {@link Manuscript}, with the {@link FormattingMenu}.
 */
public final class ChapterEditor {

    private final Manuscript manuscript;
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
    private final Drafts drafts = new Drafts();
    private final StatusBar statusBar;
    private final ErrorReporter errors;

    private BookFolder book;
    private Chapter chapter;
    private String savedText = "";

    /** @param welcome shown in the empty editor from the start until a book opens; see {@link #hideWelcome} */
    public ChapterEditor(StatusBar statusBar, ErrorReporter errors, String welcome) {

        this.statusBar = statusBar;
        this.errors = errors;

        manuscript = new Manuscript(welcome);
        manuscript.textProperty().addListener((obs, old, text) -> trackDraft());
        FormattingMenu.attach(manuscript);
    }

    public Node node() {

        return manuscript.node();
    }

    ReadOnlyStringProperty textProperty() {

        return manuscript.textProperty();
    }

    String text() {

        return manuscript.text();
    }

    /** The open chapter's text as it is on disk, without unsaved changes. */
    String savedText() {

        return savedText;
    }

    /**
     * Replaces the open chapter's whole text with {@code text}, as an unsaved change the author can undo like any edit.
     * Does nothing while no chapter is open.
     */
    void replaceAll(String text) {

        if (chapter == null)
            return;

        manuscript.replaceAll(text);
    }

    /**
     * The zero-based character of the open chapter's text the author is at: the caret's while writing, and once they
     * scroll to read, the one a third of the way down the view, until the caret moves again.
     */
    ReadOnlyIntegerProperty currentPositionProperty() {

        return manuscript.currentPositionProperty();
    }

    /** Where the manuscript's text starts, in scene coordinates, so other parts of the window can line up with it. */
    ReadOnlyDoubleProperty textMarginProperty() {

        return manuscript.textMarginProperty();
    }

    /** True once the author scrolls the manuscript to read, until the caret next moves. */
    boolean isReading() {

        return manuscript.isReading();
    }

    /** Shows the manuscript's text {@code fontSize} pixels high, its line spacing growing and shrinking with it. */
    public void setFontSize(int fontSize) {

        manuscript.setFontSize(fontSize);
    }

    /** True while the open chapter has changes that are not on disk. */
    ReadOnlyBooleanProperty dirtyProperty() {

        return dirty.getReadOnlyProperty();
    }

    boolean isDirty() {

        return dirty.get();
    }

    /** Every chapter with unsaved changes, the open one included. */
    public ObservableSet<Chapter> unsavedChapters() {

        return drafts.chapters();
    }

    /** Chapters with unsaved changes, in book order. */
    List<Chapter> sortedUnsavedChapters() {

        return drafts.sortedChapters();
    }

    boolean hasUnsavedChanges() {

        return !drafts.isEmpty();
    }

    /** The chapter being edited; empty while no chapter is open. */
    Optional<Chapter> chapter() {

        return Optional.ofNullable(chapter);
    }

    boolean isOpen(Chapter candidate) {

        return candidate.equals(chapter);
    }

    /**
     * Shows {@code chapter}, with its unsaved draft if it has one. Unsaved changes to the chapter shown so far are kept
     * as its draft.
     *
     * @return false if the chapter could not be read; the author has been told why and the editor is unchanged
     */
    boolean open(BookFolder book, Chapter chapter) {

        String text;
        try {
            text = book.read(chapter);
        } catch (UncheckedIOException e) {
            errors.report("Cannot open " + chapter.title(), e);
            return false;
        }

        this.book = book;
        this.chapter = chapter;
        savedText = text;

        manuscript.setText(drafts.text(chapter).orElse(text));
        // setText does not fire a change when the new text equals the old, so recompute the state explicitly.
        trackDraft();
        manuscript.startEditing();

        return true;
    }

    /** Removes the welcome and its logo for good, once a book is open; see {@link Manuscript#hideWelcome}. */
    void hideWelcome() {

        manuscript.hideWelcome();
    }

    /** Empties the editor and makes it read-only, discarding every draft. */
    void close() {

        drafts.clear();
        book = null;
        chapter = null;
        savedText = "";
        manuscript.clear();
        dirty.set(false);
    }

    /**
     * Saves the open chapter.
     *
     * @return false if it could not be written; the author has been told why
     */
    public boolean save() {

        if (chapter == null)
            return true;

        String text = manuscript.text();
        try {
            book.write(chapter, text);
        } catch (UncheckedIOException e) {
            errors.report("Cannot save " + chapter.title(), e);
            return false;
        }

        savedText = text;
        trackDraft();
        statusBar.info("Saved " + chapter.title());

        return true;
    }

    /**
     * Saves {@code target}, open or not: the open chapter from the editor, any other from its draft. A chapter with no
     * unsaved changes is left as it is.
     *
     * @return false if it could not be written; the author has been told why
     */
    boolean save(Chapter target) {

        if (target.equals(chapter))
            return save();

        Optional<String> draft = drafts.text(target);
        if (draft.isEmpty())
            return true;

        try {
            book.write(target, draft.get());
        } catch (UncheckedIOException e) {
            errors.report("Cannot save " + target.title(), e);
            return false;
        }

        drafts.remove(target);
        statusBar.info("Saved " + target.title());

        return true;
    }

    /**
     * Saves every chapter with unsaved changes. Chapters that cannot be written keep their drafts.
     *
     * @return false if any chapter could not be written; the author has been told why
     */
    public boolean saveAll() {

        int count = drafts.sortedChapters().size();

        try {
            drafts.saveAll(book);
        } catch (UncheckedIOException e) {
            errors.report("Some chapters could not be saved", e);
            return false;
        } finally {
            if (chapter != null && !drafts.contains(chapter)) {
                savedText = manuscript.text();
                trackDraft();
            }
        }

        statusBar.info("Saved %d %s".formatted(count, count == 1 ? "chapter" : "chapters"));

        return true;
    }

    /** Forgets every draft and shows the open chapter as it is on disk. */
    void discardAll() {

        drafts.clear();
        if (chapter != null) {
            manuscript.setText(savedText);
            trackDraft();
        }
    }

    /** Records the open chapter's text as a draft while it differs from disk, and forgets it once it matches. */
    private void trackDraft() {

        if (chapter == null) {
            dirty.set(false);
            return;
        }

        String text = manuscript.text();
        if (text.equals(savedText))
            drafts.remove(chapter);
        else
            drafts.put(chapter, text);

        dirty.set(drafts.contains(chapter));
    }
}
