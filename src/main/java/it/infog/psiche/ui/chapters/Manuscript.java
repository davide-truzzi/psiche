package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.book.model.ChapterPlace;
import it.infog.psiche.markdown.FootnoteClipboard;
import it.infog.psiche.markdown.FootnoteEdits;
import it.infog.psiche.markdown.TextBlocks;
import it.infog.psiche.settings.model.enums.EditorMode;
import it.infog.psiche.spelling.SpellChecker;
import it.infog.psiche.spelling.model.Word;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.TwoDimensional.Bias;
import org.fxmisc.richtext.util.UndoUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static it.infog.psiche.ui.preview.PreviewPane.FOLLOW_POINT;

/**
 * The page the author writes on: the open chapter's text, wrapped, in the manuscript's font, over the welcome and its
 * logo until a book opens. It knows where the author is in the text, the caret while writing and the place in view
 * while scrolling to read, and where the text starts, so the status bar can line up with it. Its text is a RichTextFX
 * {@link StyleClassedTextArea}, which only it and the parts it is made of know: the {@link PlainTextArea}, the
 * {@link TextMarks}, the {@link Repetitions}, the {@link TextSearch} and the {@link Welcome}.
 */
final class Manuscript {

    // Over writer.css's size for the manuscript, which is the default one.
    private static final String FONT_SIZE_STYLE = "-fx-font-size: %dpx;";
    // The lines of text left above a paragraph the author is taken to, as from the Media tab.
    private static final int REVEAL_CONTEXT = 3;
    // Default mode, which writer.css draws: the text keeps its styles in both modes, so a switch moves nothing.
    private static final PseudoClass DEFAULT_MODE = PseudoClass.getPseudoClass("default-mode");

    private final PlainTextArea area = new PlainTextArea();
    // The page, with its scroll bar, around the text, which draws only the paragraphs in view.
    private final VirtualizedScrollPane<StyleClassedTextArea> scrolled = new VirtualizedScrollPane<>(area);
    private final TextMarks marks = new TextMarks(area);
    private final PinMarks pins = new PinMarks(area);
    private final LinkHover linkHover = new LinkHover(area);
    private final Repetitions repetitions;
    private final TextSearch search;
    // The page that stacks the text over the welcome's logo, and the marks' dots over the text.
    private final StackPane page = new StackPane(scrolled);
    private final Welcome welcome;
    private final ReadOnlyDoubleWrapper textMargin = new ReadOnlyDoubleWrapper();
    private final ReadOnlyIntegerWrapper currentPosition = new ReadOnlyIntegerWrapper();
    // Where the right-click menu was last asked for, in the text: under the pointer, or at the caret from the keyboard.
    private int menuIndex;
    // What it was asked for on, for Default mode: the character under the pointer, or the selection from the keyboard.
    private IndexRange menuSpot = new IndexRange(0, 0);
    // True once the author scrolls the manuscript, until the caret next moves.
    private boolean reading;
    // Alt held alone, which shows the line numbers while a chapter is open.
    private final AltHold altHold = new AltHold(area);
    // True if Preferences shows the line numbers all the time, not only while Alt is held.
    private boolean lineNumbersAlways;
    private boolean chapterOpen;

    /**
     * @param welcome            shown on the empty page while no book is open; see {@link Welcome}
     * @param repetitionsCounted told the word the author is at and how many times it is in the text, while it comes
     *                           back; an empty word and 0 when it does not
     */
    Manuscript(String welcome, BiConsumer<String, Integer> repetitionsCounted) {

        area.getStyleClass().add("manuscript");
        scrolled.getStyleClass().add("manuscript-scroll");
        page.getStyleClass().add("manuscript-page");
        // The dots in the left margin, the pins in the right one, and the tip over a link, over the text.
        page.getChildren().addAll(marks.layers());
        page.getChildren().add(pins.node());
        page.getChildren().add(linkHover.layer());
        area.setWrapText(true);
        area.setEditable(false);
        // Undo takes back what the author typed, not the comments' colour and the mistakes' lines that follow it.
        area.setUndoManager(UndoUtils.plainTextUndoManager(area));
        // Kept by the area it listens to.
        new CaretBlink(area);
        repetitions = new Repetitions(area, marks::notProse, repetitionsCounted);
        search = new TextSearch(area, marks::imageAndLinkMarkers, () -> isDefaultMode() ? marks.blocks() : List.of(),
                () -> isDefaultMode() ? marks.wholeNotes() : List.of(), repetitions);
        // Each change says where it was, so the mistakes found already can move with the text.
        area.plainTextChanges().subscribe(change -> {
            marks.follow(change);
            pins.follow(change);
            repetitions.textChanged(!area.isAppEdit());
            search.textChanged();
        });
        area.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::menuRequested);
        this.welcome = new Welcome(area, page, welcome);
        showWelcome();
        altHold.heldProperty().addListener((obs, old, held) -> showLineNumbers());

        //where the text starts: the text's left edge, inside its padding
        Runnable measure = () -> textMargin.set(area.localToScene(0, 0).getX() + area.getPadding().getLeft());
        area.localToSceneTransformProperty().addListener((obs, old, transform) -> measure.run());
        area.paddingProperty().addListener((obs, old, padding) -> measure.run());

        //the current position: the caret's while writing, the one in view while scrolling to read
        area.caretPositionProperty().addListener((obs, old, caret) -> {
            reading = false;
            currentPosition.set(positionAtCaret());
        });
        area.textProperty().addListener((obs, old, text) -> {
            if (!reading)
                currentPosition.set(positionAtCaret());
        });
        // On the page rather than the text, so the wheel over the scroll bar counts too.
        scrolled.addEventFilter(ScrollEvent.SCROLL, event -> reading = true);
        scrolled.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (onScrollBar(event))
                reading = true;
        });
        // The scroll also moves while the text changes, before the view is laid out again, so look once it is.
        area.estimatedScrollYProperty().addListener((obs, old, top) -> {
            if (reading)
                Platform.runLater(() -> {
                    if (reading)
                        currentPosition.set(positionInView());
                });
        });
    }

    Node node() { return page; }

    // The text

    ObservableValue<String> textProperty() { return area.textProperty(); }
    String text() { return area.getText(); }
    int length() { return area.getLength(); }

    /** Shows {@code text} in place of the text shown, which Undo cannot bring back. */
    void setText(String text) {

        // A new text is read from its start, not from where the old one was scrolled to.
        reading = false;
        area.freely(() -> area.replaceText(text));
        area.getUndoManager().forgetHistory();
        marks.checkSpellingNow();
    }

    /** Replaces the whole text with {@code text}, as one change a single Undo reverts, and starts at the top. */
    void replaceAll(String text) {

        reading = false;
        area.freely(() -> area.replaceText(0, area.getLength(), text));

        // Typing at the top would otherwise merge into the same undo change.
        area.getUndoManager().preventMerge();
        show(ChapterPlace.START);
    }

    /** Empties the page and makes it read-only. */
    void clear() {

        setText("");
        area.setEditable(false);
    }

    /** Lets the author edit the text, or only read it, as while the book is being exported. */
    void setEditable(boolean editable) { area.setEditable(editable); }
    boolean isEditable() { return area.isEditable(); }
    ObservableBooleanValue editableProperty() { return area.editableProperty(); }

    // The welcome, while no book is open

    /** Shows the welcome as the text, read-only, and its logo, as when the app starts, once the book closes. */
    void showWelcome() {

        setText(welcome.text());
        welcome.show();
    }

    /** Takes the welcome and its logo away while a book is open, for its chapters; see {@link Welcome}. */
    void hideWelcome() {

        welcome.hide();
        setText("");
    }

    // Where the author is

    /**
     * Lets the author edit the text from {@code place}, where they left it: the caret there and that paragraph at the
     * top of the view, as far as the text still has them.
     */
    void startEditing(ChapterPlace place) {

        area.setEditable(true);
        show(place);
    }

    /** Where the author is in the text: the caret, and the paragraph at the top of the view. */
    ChapterPlace place() {

        int top = area.getVisibleParagraphs().isEmpty() ? 0 : area.firstVisibleParToAllParIndex();
        return new ChapterPlace(positionAtCaret(), top);
    }

    /** Where the caret is in the text. */
    int caret() { return positionAtCaret(); }

    /**
     * Puts the caret at {@code position}, with its paragraph a few lines below the top of the view, and the focus, so
     * what the author was shown has some text above it.
     */
    void reveal(int position) {

        int paragraph = area.offsetToPosition(Math.clamp(position, 0, area.getLength()), Bias.Forward).getMajor();
        show(new ChapterPlace(position, Math.max(0, paragraph - REVEAL_CONTEXT)));
    }

    /**
     * The zero-based character of the text the author is at: the caret's while writing, and once they scroll to read,
     * the one a third of the way down the view, until the caret moves again.
     */
    ReadOnlyIntegerProperty currentPositionProperty() {

        return currentPosition.getReadOnlyProperty();
    }

    /** True once the author scrolls the manuscript to read, until the caret next moves. */
    boolean isReading() { return reading; }

    /** Where the text starts, in scene coordinates, so other parts of the window can line up with it. */
    ReadOnlyDoubleProperty textMarginProperty() {

        return textMargin.getReadOnlyProperty();
    }

    // How it looks

    /** Shows the text {@code fontSize} pixels high, its line spacing growing and shrinking with it. */
    void setFontSize(int fontSize) {

        area.setStyle(FONT_SIZE_STYLE.formatted(fontSize));
    }

    /** Shows the line numbers all the time, as Preferences asks, or only while Alt is held alone. */
    void setLineNumbersAlways(boolean always) {

        lineNumbersAlways = always;
        showLineNumbers();
    }

    /** Lets the Markdown recede, in {@link EditorMode#DEFAULT}, or shows it as written, as Preferences asks. */
    void setMode(EditorMode mode) {

        boolean on = mode == EditorMode.DEFAULT;
        if (area.getPseudoClassStates().contains(DEFAULT_MODE) == on)
            return;

        area.pseudoClassStateChanged(DEFAULT_MODE, on);
        area.setBlocks(on ? marks::blocks : List::of);
        area.setWidening(on ? FootnoteEdits::withNotes : PlainTextArea.Widening.NONE);
        area.setClipboard(on ? FootnoteClipboard::copied : PlainTextArea.Copying.PLAIN,
                on ? FootnoteClipboard::pasted : PlainTextArea.Pasting.PLAIN);
        // RichTextFX draws the tints as it lays a paragraph out, not as their colour changes; those out of view will.
        for (Node paragraph : area.lookupAll(".paragraph-text"))
            if (paragraph instanceof Parent text)
                text.requestLayout();
    }

    /** The search of the text, which the find bar drives. */
    TextSearch search() { return search; }
    /** The pinned notes' marks in the right margin, which follow their paragraphs. */
    PinMarks pins() { return pins; }

    /** Shows the other places of the word the author is at, as Preferences asks, or none. */
    void highlightRepetitions(boolean on) {

        repetitions.setOn(on);
    }

    // Its marks: spelling mistakes, missing images and links

    /**
     * Checks the text's spelling with {@code checker} from now on, {@code ownWords} being right too, and the words that
     * look like names too if {@code strict}; with none, the text is not checked and shows no mistakes.
     */
    void checkSpelling(SpellChecker checker, Set<String> ownWords, boolean strict) {

        marks.checkSpelling(checker, ownWords, strict);
    }

    /** The spelling mistake the right-click menu was opened on, with the word as it is written; none if it was not. */
    Optional<Word> menuMistake() {

        return marks.mistakeAt(menuIndex).map(range -> new Word(area.getText(range.start(), range.end()), range));
    }

    /** Up to five words the author may have meant by {@code word}, the likeliest first. */
    List<String> suggestions(String word) {

        return marks.suggestions(word);
    }

    /** Looks for the text's images and links as those of {@code chapter}; none with {@code null}. */
    void setChapter(Chapter chapter) {

        marks.setChapter(chapter);
        chapterOpen = chapter != null;
        showLineNumbers();
    }

    /** Opens with {@code open} a link the author Ctrl-clicks, if {@code opens} says it leads somewhere. */
    void setLinkOpening(Predicate<String> opens, Consumer<String> open) { linkHover.setOpening(opens, open); }

    /** Checks the links to other chapters against {@code anchors}, each chapter's; none is broken with {@code null}. */
    void setBookAnchors(Map<String, Set<String>> anchors) {

        marks.setBookAnchors(anchors);
    }

    // Editing, for the formatting menu

    /**
     * Gives the text {@code menu} on a right-click, and runs {@code shortcuts} ahead of the text's own key handling.
     */
    void setMenu(ContextMenu menu, Map<KeyCombination, Runnable> shortcuts) {

        area.setContextMenu(menu);
        area.addEventFilter(KeyEvent.KEY_PRESSED, event -> shortcuts.entrySet().stream()
                .filter(shortcut -> shortcut.getKey().match(event))
                .findFirst()
                .ifPresent(shortcut -> {
                    shortcut.getValue().run();
                    event.consume();
                }));
    }

    /** True if the selection can be formatted: in Default mode, only if the links and image lines stay whole. */
    boolean canFormat() {

        IndexRange selection = area.getSelection();
        return !isDefaultMode() || marks.formattable(selection.getStart(), selection.getEnd());
    }

    /**
     * What the menu's link and image items act on: in Default mode, the link or image under the right-click, as the
     * character there, or the selection if the menu came from the keyboard; in Advanced, the selection, as in a text
     * editor.
     */
    IndexRange menuSelection() { return isDefaultMode() ? menuSpot : area.getSelection(); }

    IndexRange selection() { return area.getSelection(); }
    /** True in Default mode, where the Markdown recedes and links, images and footnotes are blocks. */
    boolean isDefaultMode() { return area.getPseudoClassStates().contains(DEFAULT_MODE); }
    /** True if {@code position} lies inside one of Default mode's blocks, past its start and before its end. */
    boolean inBlock(int position) {

        return isDefaultMode() && TextBlocks.inside(marks.blocks(), position).isPresent();
    }
    String selectedText() { return area.getSelectedText(); }
    void select(int start, int end) { area.selectRange(start, end); }
    /**
     * Replaces the text from {@code start} to {@code end} with {@code replacement}, as one change Undo reverts; an
     * edit of the app's own, which Default mode's blocks never refuse.
     */
    void replace(int start, int end, String replacement) {

        area.freely(() -> area.replaceText(start, end, replacement));
    }
    boolean canUndo() { return area.isUndoAvailable(); }
    boolean canRedo() { return area.isRedoAvailable(); }
    void undo() { area.undo(); }
    void redo() { area.redo(); }
    void cut() { area.cut(); }
    void copy() { area.copy(); }
    void paste() { area.paste(); }
    void deleteSelection() { area.deleteText(area.getSelection()); }
    void selectAll() { area.selectAll(); }
    void focus() { area.requestFocus(); }

    /** Puts the caret at {@code place}, with its paragraph at the top of the view, and the focus. */
    private void show(ChapterPlace place) {

        area.moveTo(Math.clamp(place.caret(), 0, area.getLength()));
        // Following the caret would scroll by a fraction of a line, blurring the text and moving it each time.
        area.showParagraphAtTop(Math.clamp(place.topParagraph(), 0, area.getParagraphs().size() - 1));
        area.requestFocus();
    }

    /** Remembers where the right-click menu is asked for: see {@link #menuIndex} and {@link #menuSpot}. */
    private void menuRequested(ContextMenuEvent event) {

        if (event.isKeyboardTrigger()) {
            menuIndex = area.getCaretPosition();
            menuSpot = area.getSelection();
            return;
        }

        CharacterHit hit = area.hit(event.getX(), event.getY());
        menuIndex = hit.getInsertionIndex();
        menuSpot = hit.getCharacterIndex().isPresent()
                ? new IndexRange(hit.getCharacterIndex().getAsInt(), hit.getCharacterIndex().getAsInt() + 1)
                : new IndexRange(menuIndex, menuIndex);
    }


    private int positionAtCaret() {

        // The caret can briefly lie past the end while the text is being replaced.
        return Math.min(area.getCaretPosition(), area.getLength());
    }

    /** The character a third of the way down the view, where the preview puts the position it follows. */
    private int positionInView() {

        int index = area.hit(area.getPadding().getLeft(), area.getHeight() * FOLLOW_POINT).getInsertionIndex();
        return Math.min(index, area.getLength());
    }

    private static boolean onScrollBar(MouseEvent event) {

        for (Node node = event.getPickResult().getIntersectedNode(); node != null; node = node.getParent())
            if (node instanceof ScrollBar)
                return true;
        return false;
    }

    /** Shows the line numbers while a chapter is open, if Preferences always shows them or Alt is held alone. */
    private void showLineNumbers() {

        marks.showLineNumbers(chapterOpen && (lineNumbersAlways || altHold.isHeld()));
    }
}
