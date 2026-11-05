package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.spelling.SpellChecker;
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
    private final Supplier<Set<String>> ownWords;
    private final Consumer<String> addOwnWord;
    private final Spelling spelling = new Spelling(this::checkSpelling);
    private Optional<SpellChecker> checker = Optional.empty();
    private boolean strictSpelling;

    private BookFolder book;
    private Chapter chapter;
    private String savedText = "";

    /**
     * @param welcome  shown in the empty editor from the start until a book opens; see {@link #hideWelcome}
     * @param ownWords   the words the author made right for the spell checker, read when it starts checking
     * @param addOwnWord adds a word to them, for every book
     */
    public ChapterEditor(StatusBar statusBar, ErrorReporter errors, String welcome, Supplier<Set<String>> ownWords,
                         Consumer<String> addOwnWord) {

        this.statusBar = statusBar;
        this.errors = errors;
        this.ownWords = ownWords;
        this.addOwnWord = addOwnWord;

        manuscript = new Manuscript(welcome, statusBar::showRepetitions);
        manuscript.textProperty().addListener((obs, old, text) -> trackDraft());
        SpellingMenu.attach(FormattingMenu.attach(manuscript), manuscript, this::makeRight);
    }

    public Node node() {

        return manuscript.node();
    }

    ObservableValue<String> textProperty() {

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

    /**
     * Turns spell checking on or off, as Preferences asks, and with it on, checks the words with a capital letter too,
     * which names have, if {@code strict}.
     */
    public void setSpelling(boolean on, boolean strict) {

        boolean strictChanged = strict != strictSpelling;
        strictSpelling = strict;
        spelling.setOn(on);
        if (strictChanged)
            checkSpelling(checker);
    }

    /** Shows, or not, as Preferences asks, the other places of the word the author is at, and how many there are. */
    public void setHighlightRepetitions(boolean on) {

        manuscript.highlightRepetitions(on);
    }

    /** Checks the spelling in the open book's language, a BCP 47 tag; none with {@code null} while no book is open. */
    void setBookLanguage(String tag) {

        spelling.setBookLanguage(tag);
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

    /** Makes {@code word} right in every book, and checks the text again, where it is right at once. */
    private void makeRight(String word) {

        addOwnWord.accept(word);
        checkSpelling(checker);
        statusBar.info("\"%s\" will not be marked as a mistake again, in any book".formatted(word));
    }

    private void checkSpelling(Optional<SpellChecker> checker) {

        this.checker = checker;
        manuscript.checkSpelling(checker.orElse(null), checker.isPresent() ? ownWords.get() : Set.of(), strictSpelling);
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
