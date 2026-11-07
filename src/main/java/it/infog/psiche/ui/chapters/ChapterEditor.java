package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.settings.model.enums.EditorMode;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableSet;
import javafx.scene.Node;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private final ChapterSpelling spelling;
    private final ChapterPlaces places;
    private final ChapterEdits edits;
    private final ChapterLinks links;
    private Runnable insertImage = () -> {};

    private BookFolder book;
    private Chapter chapter;
    private String savedText = "";

    /**
     * @param welcome  shown in the empty editor while no book is open; see {@link #hideWelcome}
     * @param ownWords   the words the author made right for the spell checker, read when it starts checking
     * @param addOwnWord adds a word to them, for every book
     */
    public ChapterEditor(StatusBar statusBar, ErrorReporter errors, String welcome, Supplier<Set<String>> ownWords,
                         Consumer<String> addOwnWord) {

        this.statusBar = statusBar;
        this.errors = errors;

        manuscript = new Manuscript(welcome, statusBar::showRepetitions);
        spelling = new ChapterSpelling(manuscript, statusBar, ownWords, addOwnWord);
        places = new ChapterPlaces(manuscript, () -> chapter);
        edits = new ChapterEdits(manuscript, this::isOpen);
        links = new ChapterLinks(manuscript, this::chapter);
        manuscript.textProperty().addListener((obs, old, text) -> trackDraft());
        SpellingMenu.attach(FormattingMenu.attach(manuscript, () -> insertImage.run(), links.actions()), manuscript,
                spelling::makeRight);
    }

    // The page

    public Node node() { return manuscript.node(); }
    public ObservableValue<String> textProperty() { return manuscript.textProperty(); }
    public String text() { return manuscript.text(); }
    /** The open chapter's text as it is on disk, without unsaved changes. */
    String savedText() { return savedText; }
    /**
     * The zero-based character of the open chapter's text the author is at: the caret's while writing, and once they
     * scroll to read, the one a third of the way down the view, until the caret moves again.
     */
    ReadOnlyIntegerProperty currentPositionProperty() { return manuscript.currentPositionProperty(); }
    /** Where the manuscript's text starts, in scene coordinates, so other parts of the window can line up with it. */
    ReadOnlyDoubleProperty textMarginProperty() { return manuscript.textMarginProperty(); }
    /** True once the author scrolls the manuscript to read, until the caret next moves. */
    boolean isReading() { return manuscript.isReading(); }
    /** Takes the author to {@code position} in the open chapter, with the caret and the focus there. */
    public void reveal(int position) { manuscript.reveal(position); }
    /** Shows the welcome and its logo again, as the book closes; see {@link Manuscript#showWelcome}. */
    void showWelcome() { manuscript.showWelcome(); }
    /** Removes the welcome and its logo while a book is open; see {@link Manuscript#hideWelcome}. */
    void hideWelcome() { manuscript.hideWelcome(); }

    /**
     * Replaces the open chapter's whole text with {@code text}, as an unsaved change the author can undo like any edit.
     * Does nothing while no chapter is open.
     */
    void replaceAll(String text) {

        if (chapter == null)
            return;

        manuscript.replaceAll(text);
    }

    // Preferences, and the book's language

    /** Shows the manuscript's text {@code fontSize} pixels high, its line spacing growing and shrinking with it. */
    public void setFontSize(int fontSize) { manuscript.setFontSize(fontSize); }
    /**
     * Turns spell checking on or off, as Preferences asks, and with it on, checks the words with a capital letter too,
     * which names have, if {@code strict}.
     */
    public void setSpelling(boolean on, boolean strict) { spelling.setOn(on, strict); }
    /** Shows, or not, as Preferences asks, the other places of the word the author is at, and how many there are. */
    public void setHighlightRepetitions(boolean on) { manuscript.highlightRepetitions(on); }
    /** Shows the Markdown receding, in {@link EditorMode#DEFAULT}, or as written, as Preferences asks. */
    public void setEditorMode(EditorMode mode) { manuscript.setMode(mode); }
    /** Checks the spelling in the open book's language, a BCP 47 tag; none with {@code null} while no book is open. */
    void setBookLanguage(String tag) { spelling.setBookLanguage(tag); }

    // The open chapter, and the unsaved ones

    /** True while the open chapter has changes that are not on disk. */
    ReadOnlyBooleanProperty dirtyProperty() { return dirty.getReadOnlyProperty(); }
    boolean isDirty() { return dirty.get(); }
    /** Every chapter with unsaved changes, the open one included. */
    public ObservableSet<Chapter> unsavedChapters() { return drafts.chapters(); }
    /** Chapters with unsaved changes, in book order. */
    List<Chapter> sortedUnsavedChapters() { return drafts.sortedChapters(); }
    boolean hasUnsavedChanges() { return !drafts.isEmpty(); }
    /** The chapter being edited; empty while no chapter is open. */
    public Optional<Chapter> chapter() { return Optional.ofNullable(chapter); }
    /** The book whose chapter is edited; empty while no chapter is open. */
    public Optional<BookFolder> book() { return chapter().map(open -> book); }
    public boolean isOpen(Chapter candidate) { return candidate.equals(chapter); }

    // Its parts, for the rest of the window

    /** Changes made to the open chapter from outside its text, as the Media tab makes them. */
    public ChapterEdits edits() { return edits; }
    /** Where the author left each chapter of the open book, where it opens again. */
    ChapterPlaces places() { return places; }
    /** The open chapter's links: checked against the book's headings, and followed from the preview. */
    ChapterLinks links() { return links; }
    /** Opens the web and mail links the author Ctrl-clicks in the manuscript with {@code browser}. */
    public void setBrowser(Consumer<String> browser) { links.setBrowser(browser); }
    /** Runs {@code action} when the author asks to insert an image, from the manuscript's right-click menu. */
    public void setOnInsertImage(Runnable action) { insertImage = action; }

    // Opening, closing and saving

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

        places.remember();
        this.book = book;
        this.chapter = chapter;
        savedText = text;

        manuscript.setChapter(chapter);
        manuscript.setText(drafts.text(chapter).orElse(text));
        // setText does not fire a change when the new text equals the old, so recompute the state explicitly.
        trackDraft();
        manuscript.startEditing(places.of(chapter));

        return true;
    }

    /**
     * Keeps the open chapter from changing while {@code frozen}, as while the book is being exported: it can be read,
     * but not edited.
     */
    public void setFrozen(boolean frozen) { manuscript.setEditable(!frozen && chapter != null); }

    /** Empties the editor and makes it read-only, discarding every draft. */
    void close() {

        places.remember();
        drafts.clear();
        book = null;
        chapter = null;
        savedText = "";
        manuscript.setChapter(null);
        links.clear();
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
