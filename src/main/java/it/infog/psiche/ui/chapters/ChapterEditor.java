package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.markdown.MarkdownFormatter;
import it.infog.psiche.markdown.model.TextEdit;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.Logo;
import it.infog.psiche.ui.common.StatusBar;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.collections.ObservableSet;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static it.infog.psiche.ui.common.Controls.menuItem;

/**
 * The manuscript editor: one chapter at a time. Switching chapters never writes to disk; unsaved text stays in memory
 * as a draft, is restored when the author returns to that chapter, and is written only by {@link #save()} or
 * {@link #saveAll()}.
 */
public final class ChapterEditor {

    // Formatting shortcuts, as in common word processors and Markdown editors; Shortcut is Ctrl, or ⌘ on macOS.
    private static final KeyCombination BOLD = new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination ITALIC = new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination NUMBERED_LIST =
            new KeyCodeCombination(KeyCode.DIGIT7, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination BULLETED_LIST =
            new KeyCodeCombination(KeyCode.DIGIT8, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination QUOTE =
            new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination COMMENT =
            new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination CLEAR_FORMATTING =
            new KeyCodeCombination(KeyCode.BACK_SLASH, KeyCombination.SHORTCUT_DOWN);

    private final TextArea manuscript = new TextArea();
    // The logo behind the welcome, and the page that stacks the manuscript over it.
    private final StackPane welcomeLogo = new StackPane();
    private final StackPane page = new StackPane(welcomeLogo, manuscript);
    private final Map<KeyCombination, Runnable> shortcuts = new LinkedHashMap<>();
    private final ReadOnlyDoubleWrapper textMargin = new ReadOnlyDoubleWrapper();
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
    private final ReadOnlyIntegerWrapper currentPosition = new ReadOnlyIntegerWrapper();
    private final Drafts drafts = new Drafts();
    private final StatusBar statusBar;
    private final ErrorReporter errors;

    private BookFolder book;
    private Chapter chapter;
    private String savedText = "";
    // True once the author scrolls the manuscript, until the caret next moves.
    private boolean reading;

    /** @param welcome shown in the empty editor from the start until a book opens; see {@link #hideWelcome} */
    public ChapterEditor(StatusBar statusBar, ErrorReporter errors, String welcome) {

        this.statusBar = statusBar;
        this.errors = errors;

        manuscript.getStyleClass().add("manuscript");
        manuscript.setWrapText(true);
        manuscript.setEditable(false);
        manuscript.setPromptText(welcome);
        manuscript.textProperty().addListener((obs, old, text) -> trackDraft());

        //the welcome's logo, sized to the page and never in the way of the page's own size or the mouse
        welcomeLogo.getChildren().add(Logo.lockup(welcomeLogo, 0.6));
        welcomeLogo.getStyleClass().add("welcome-logo");
        welcomeLogo.setMinSize(0, 0);
        welcomeLogo.setPrefSize(0, 0);
        welcomeLogo.setMouseTransparent(true);

        // The skin adds its nodes after the skin property changes, so look for them once it has.
        manuscript.skinProperty().addListener((obs, old, skin) -> Platform.runLater(() -> {
            fitContentToText();
            trackTextMargin();
        }));

        //formatting menu, and its shortcuts ahead of the text area's own key handling
        manuscript.setContextMenu(contextMenu());
        manuscript.addEventFilter(KeyEvent.KEY_PRESSED, event -> shortcuts.entrySet().stream()
                .filter(shortcut -> shortcut.getKey().match(event))
                .findFirst()
                .ifPresent(shortcut -> {
                    shortcut.getValue().run();
                    event.consume();
                }));

        //the current position: the caret's while writing, the one in view while scrolling to read
        manuscript.caretPositionProperty().addListener((obs, old, caret) -> {
            reading = false;
            currentPosition.set(positionAtCaret());
        });
        manuscript.textProperty().addListener((obs, old, text) -> {
            if (!reading)
                currentPosition.set(positionAtCaret());
        });
        manuscript.addEventFilter(ScrollEvent.SCROLL, event -> reading = true);
        manuscript.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (onScrollBar(event))
                reading = true;
        });
        manuscript.scrollTopProperty().addListener((obs, old, top) -> {
            if (reading)
                currentPosition.set(positionInView());
        });
    }

    public Node node() {

        return page;
    }

    ReadOnlyStringProperty textProperty() {

        return manuscript.textProperty();
    }

    String text() {

        return manuscript.getText();
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

        // Unlike setText, which clears the undo history, replaceText records one change that a single Undo reverts.
        manuscript.replaceText(0, manuscript.getLength(), text);

        // Typing at the end of the replaced text would merge into the same undo change; start at the top instead.
        manuscript.positionCaret(0);
        manuscript.requestFocus();
    }

    /**
     * The zero-based character of the open chapter's text the author is at: the caret's while writing, and once they
     * scroll to read, the one a third of the way down the view, until the caret moves again.
     */
    ReadOnlyIntegerProperty currentPositionProperty() {

        return currentPosition.getReadOnlyProperty();
    }

    /** Where the manuscript's text starts, in scene coordinates, so other parts of the window can line up with it. */
    ReadOnlyDoubleProperty textMarginProperty() {

        return textMargin.getReadOnlyProperty();
    }

    /** True once the author scrolls the manuscript to read, until the caret next moves. */
    boolean isReading() {

        return reading;
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
        manuscript.setEditable(true);
        manuscript.positionCaret(0);
        manuscript.requestFocus();

        return true;
    }

    /**
     * Removes the welcome and its logo for good, once a book is open. The welcome is the text area's prompt, which
     * shows whenever the text is empty, so it would otherwise fill every empty or new chapter as if it were the
     * chapter's text.
     */
    void hideWelcome() {

        manuscript.setPromptText(null);
        page.getChildren().remove(welcomeLogo);
    }

    /** Empties the editor and makes it read-only, discarding every draft. */
    void close() {

        drafts.clear();
        book = null;
        chapter = null;
        savedText = "";
        manuscript.clear();
        manuscript.setEditable(false);
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

        String text = manuscript.getText();
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
                savedText = manuscript.getText();
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

        String text = manuscript.getText();
        if (text.equals(savedText))
            drafts.remove(chapter);
        else
            drafts.put(chapter, text);

        dirty.set(drafts.contains(chapter));
    }

    /**
     * Markdown formatting for the selection, above the editing commands the text area's own menu has, which this
     * menu replaces.
     */
    private ContextMenu contextMenu() {

        //formatting
        MenuItem bold = formatItem("Bold", BOLD, MarkdownFormatter::bold);
        MenuItem italic = formatItem("Italic", ITALIC, MarkdownFormatter::italic);
        Menu heading = new Menu("Heading");
        for (int level = 1; level <= 4; level++)
            heading.getItems().add(formatItem("Heading " + level,
                    new KeyCodeCombination(KeyCode.valueOf("DIGIT" + level), KeyCombination.SHORTCUT_DOWN),
                    headingFormat(level)));
        MenuItem bulletedList = formatItem("Bulleted list", BULLETED_LIST, MarkdownFormatter::bulletedList);
        MenuItem numberedList = formatItem("Numbered list", NUMBERED_LIST, MarkdownFormatter::numberedList);
        MenuItem quote = formatItem("Quote", QUOTE, MarkdownFormatter::quote);
        MenuItem comment = formatItem("Comment", COMMENT, MarkdownFormatter::comment);
        MenuItem clear = formatItem("Clear formatting", CLEAR_FORMATTING, MarkdownFormatter::clearFormatting);

        //editing
        MenuItem undo = menuItem("Undo", manuscript::undo);
        MenuItem redo = menuItem("Redo", manuscript::redo);
        MenuItem cut = menuItem("Cut", manuscript::cut);
        MenuItem copy = menuItem("Copy", manuscript::copy);
        MenuItem paste = menuItem("Paste", manuscript::paste);
        MenuItem delete = menuItem("Delete", () -> manuscript.deleteText(manuscript.getSelection()));
        MenuItem selectAll = menuItem("Select All", manuscript::selectAll);

        ContextMenu menu = new ContextMenu(bold, italic, heading, bulletedList, numberedList, quote, comment, clear,
                new SeparatorMenuItem(), undo, redo, new SeparatorMenuItem(), cut, copy, paste, delete,
                new SeparatorMenuItem(), selectAll);

        // Enable what applies to the text and selection as they are when the menu opens.
        menu.setOnShowing(event -> {
            boolean editable = manuscript.isEditable();
            IndexRange selection = manuscript.getSelection();
            boolean selected = selection.getLength() > 0;
            boolean words = !manuscript.getSelectedText().isBlank();
            boolean oneLine = MarkdownFormatter.spansOneLine(manuscript.getText(), selection.getStart(), selection.getEnd());

            bold.setDisable(!editable || !words);
            italic.setDisable(!editable || !words);
            heading.setDisable(!editable || !oneLine);
            List.of(bulletedList, numberedList, quote, comment, clear).forEach(item -> item.setDisable(!editable));

            undo.setDisable(!editable || !manuscript.isUndoable());
            redo.setDisable(!editable || !manuscript.isRedoable());
            cut.setDisable(!editable || !selected);
            copy.setDisable(!selected);
            paste.setDisable(!editable || !Clipboard.getSystemClipboard().hasString());
            delete.setDisable(!editable || !selected);
            selectAll.setDisable(manuscript.getLength() == 0);
        });

        return menu;
    }

    /** A menu item that formats the selection, also run by {@code shortcut} while the editor has the focus. */
    private MenuItem formatItem(String text, KeyCombination shortcut, Formatting formatting) {

        MenuItem item = menuItem(text, () -> format(formatting));
        item.setAccelerator(shortcut);
        shortcuts.put(shortcut, () -> format(formatting));

        return item;
    }

    /** A heading of {@code level}, which a selection over several lines does not become. */
    private static Formatting headingFormat(int level) {

        return (text, start, end) -> MarkdownFormatter.spansOneLine(text, start, end)
                ? MarkdownFormatter.heading(text, start, end, level)
                : TextEdit.selecting(start, end, text.substring(start, end));
    }

    /** Formats the selection as one change, which a single Undo reverts, and selects the result. */
    private void format(Formatting formatting) {

        if (!manuscript.isEditable())
            return;

        String text = manuscript.getText();
        IndexRange selection = manuscript.getSelection();
        TextEdit edit = formatting.apply(text, selection.getStart(), selection.getEnd());

        // Replacing text with itself would still add a step to Undo.
        if (!edit.replacement().equals(text.substring(edit.start(), edit.end())))
            manuscript.replaceText(edit.start(), edit.end(), edit.replacement());
        manuscript.selectRange(edit.selectionStart(), edit.selectionEnd());
    }

    /**
     * Keeps the scrollable content at least as tall as the text it draws. The text area sizes its content without the
     * line spacing writer.css gives the text, so the end of a long chapter could not be scrolled to.
     */
    private void fitContentToText() {

        if (!(manuscript.lookup(".content") instanceof Region content))
            return;

        // The prompt is a .text node of its own, directly in the content; the chapter's text sits in a group.
        content.lookupAll(".text").stream()
                .filter(node -> node instanceof Text && node.getParent() != content)
                .findFirst()
                .ifPresent(text -> {
                    Runnable fit = () -> content.setMinHeight(text.getLayoutBounds().getHeight()
                            + content.getPadding().getTop() + content.getPadding().getBottom());
                    text.layoutBoundsProperty().addListener((obs, old, bounds) -> fit.run());
                    content.paddingProperty().addListener((obs, old, padding) -> fit.run());
                    fit.run();
                });
    }

    /** Keeps {@link #textMarginProperty()} at the left edge of the text, inside the content's padding. */
    private void trackTextMargin() {

        if (!(manuscript.lookup(".content") instanceof Region content))
            return;

        Runnable measure = () -> textMargin.set(content.localToScene(0, 0).getX() + content.getPadding().getLeft());
        manuscript.localToSceneTransformProperty().addListener((obs, old, transform) -> measure.run());
        content.paddingProperty().addListener((obs, old, padding) -> measure.run());
        measure.run();
    }

    private int positionAtCaret() {

        // The caret can briefly lie past the end while the text is being replaced.
        return Math.min(manuscript.getCaretPosition(), manuscript.getLength());
    }

    /** The character a third of the way down the view, where the preview puts the position it follows. */
    private int positionInView() {

        if (!(manuscript.getSkin() instanceof TextAreaSkin skin))
            return currentPosition.get();

        // The skin hit-tests in the coordinates of the scrolled content, so the view starts at the scroll offset.
        int index = skin.getIndex(0, manuscript.getScrollTop() + manuscript.getHeight() / 3).getInsertionIndex();
        return Math.min(index, manuscript.getLength());
    }

    private static boolean onScrollBar(MouseEvent event) {

        for (Node node = event.getPickResult().getIntersectedNode(); node != null; node = node.getParent())
            if (node instanceof ScrollBar)
                return true;
        return false;
    }

    /** Turns the text and selection into the edit that formats it, as the {@link MarkdownFormatter} methods do. */
    @FunctionalInterface
    private interface Formatting {
        TextEdit apply(String text, int start, int end);
    }
}
