package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.notes.model.BoardView;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.notes.card.NoteCard;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * The Notes tab: the open chapter's {@link NoteBoard}, shown in a {@link BoardViewport}, the author moving around it
 * with the pointer, the wheel, the keys or the corner bar; and a message while there is no chapter open, or no note on
 * its board yet. The files' copying and pictures are {@link NoteFiles}'.
 * <p>
 * The notes are read as soon as their chapter opens, so the tab's title counts them even behind another tab, and every
 * change is written, on the {@link NotesThread}. Each chapter's board reopens where it was last looked at, once the
 * board has a size to show it in. Pictures of images and PDFs are made only while the tab is showing.
 */
public final class NotesPane extends StackPane {

    /** Runs when a book's first note is made, to keep {@code notes/} out of Git. */
    @FunctionalInterface
    public interface FirstNoteListener {

        void firstNoteCreated();
    }

    private static final String EMPTY_HINT = "Double-click anywhere to write a note,\nor drop files here.";

    private final NoteBoard board;
    private final BoardViewport viewport;
    private final Label message = new Label();
    private final BoardBar bar;
    private final ReadOnlyBooleanWrapper available = new ReadOnlyBooleanWrapper();
    private final BooleanProperty showing = new SimpleBooleanProperty();
    private final NoteFiles files;
    private final NotesThread notes;

    private boolean loaded;

    /** What a chapter's board shows when it opens. */
    private record Loaded(List<Note> notes,
                          BoardView view) {
    }

    /** @param browser opens an address, here a note's file or its folder, in the system's default program */
    public NotesPane(StatusBar statusBar, ErrorReporter errors, FirstNoteListener firstNote,
                     Consumer<String> browser) {

        this.files = new NoteFiles(browser);
        this.notes = new NotesThread(errors);

        //board, inside a viewport that shows the part in view
        board = new NoteBoard(notes, files, statusBar, errors, showing, firstNote, this::reloadShown);
        viewport = new BoardViewport(board, this::saveView, () -> board.select(null));

        message.getStyleClass().add("notes-message");
        message.setMouseTransparent(true);

        bar = new BoardBar(viewport, () -> board.createAround(viewport.centre()));
        StackPane.setAlignment(bar, Pos.BOTTOM_RIGHT);

        getChildren().addAll(viewport, message, bar);
        getStyleClass().add("notes-pane");
        addEventHandler(KeyEvent.KEY_PRESSED, this::keyPressed);

        showing.addListener((obs, was, isShowing) -> {
            if (isShowing)
                board.showPictures();
        });
        board.countProperty().addListener((obs, old, count) -> updateMessage());
        updateMessage();
    }

    /** Whether the tab is on screen: pictures are made only while it is. Bind it to the tab's selection. */
    public BooleanProperty showingProperty() {

        return showing;
    }

    /** "Notes", or "Notes (3)" with the open chapter's count, for the tab's title. */
    public StringBinding titleBinding() {

        ReadOnlyIntegerProperty count = board.countProperty();
        return Bindings.createStringBinding(() -> count.get() == 0 ? "Notes" : "Notes (" + count.get() + ")", count);
    }

    /** True while a note on the board carries the Duplicate on Import tag. */
    public ReadOnlyBooleanProperty duplicatesProperty() {

        return board.duplicatesProperty();
    }

    /** True while a book is open; the tab is disabled otherwise. */
    public ReadOnlyBooleanProperty availableProperty() {

        return available.getReadOnlyProperty();
    }

    /** @param chapters gives the book's chapters, by name, in their order, which a note can be moved to */
    public void setChapters(Supplier<List<String>> chapters) {

        board.setChapters(chapters);
    }

    /** Runs {@code binned} each time a note, or a file a note no longer carries, has gone to Detached notes. */
    public void setOnBinned(Runnable binned) {

        board.setOnBinned(binned);
    }

    /**
     * Shows the notes of {@code chapter} in {@code store}, saving first what the board it replaces still had to save.
     *
     * @param store   the open book's notes, or {@code null} with no book open
     * @param chapter the open chapter's name, or {@code null} with none open
     */
    public void show(NoteStore store, String chapter) {

        if (!notes.shows(store, chapter))
            open(store, chapter);
    }

    /** Reads the notes of {@code chapter} again if its board is shown, as notes were put back on it. */
    public void reload(String chapter) {

        if (chapter.equals(notes.chapter()))
            open(notes.store(), chapter);
    }

    /**
     * Reads again the notes of the board shown, if any, as an import may have changed them, or a note could not leave
     * the board.
     */
    void reloadShown() {

        if (notes.chapter() != null)
            open(notes.store(), notes.chapter());
    }

    /**
     * Saves now whatever is waiting: text the author has not paused on, and where the board is looked at; then waits
     * for the notes thread to write it, so a chapter can be renamed or deleted, or the app closed, safely.
     */
    public void settle() {

        board.cards().forEach(NoteCard::flushText);
        viewport.settle();
        notes.settle();
    }

    /** The thread the board's notes are read and written on, for other work on the notes to run in turn with it. */
    NotesThread thread() {

        return notes;
    }

    /** Stops the notes thread once it has written what it was given, and the files' threads at once. */
    public void shutdown() {

        notes.shutdown();
        files.shutdown();
    }

    // Loading

    /** Saves what the board shown still had to save, then shows the notes of {@code chapter} in {@code store}. */
    private void open(NoteStore store, String chapter) {

        settle();
        board.clear();
        viewport.reset();
        notes.show(store, chapter);
        available.set(store != null);
        loaded = false;

        load();
        updateMessage();
    }

    private void load() {

        notes.read("Cannot read the notes of " + notes.chapter(),
                (store, chapter) -> new Loaded(store.notes(chapter), store.boardView(chapter)), this::loaded);
    }

    /** Shows the notes read, and the board where it was last looked at. */
    private void loaded(Loaded result) {

        board.show(result.notes());
        loaded = true;
        updateMessage();
        viewport.open(result.view());
    }

    private void updateMessage() {

        boolean hasChapter = notes.chapter() != null;
        viewport.setVisible(hasChapter);
        bar.setVisible(hasChapter);

        message.setText(!hasChapter ? "Open a chapter to see its notes." : EMPTY_HINT);
        message.setVisible(!hasChapter || (loaded && board.countProperty().get() == 0));
    }

    private void saveView(BoardView view) {

        notes.write("Cannot remember where the board was", (store, chapter) -> store.saveBoardView(chapter, view));
    }

    // Keys

    /**
     * Ctrl/⌘ with + − 0 zoom, with V pastes into the selected note or on the board; Delete moves the selected note to
     * Detached notes; Escape leaves it.
     */
    private void keyPressed(KeyEvent event) {

        KeyCode code = event.getCode();

        // In a note's text, the keys are the text's, but Escape leaves it.
        if (event.getTarget() instanceof TextInputControl) {
            if (code == KeyCode.ESCAPE) {
                viewport.requestFocus();
                event.consume();
            }
            return;
        }

        if ((event.isShortcutDown() && shortcutKey(code)) || boardKey(code))
            event.consume();
    }

    /** Ctrl/⌘ with {@code code}: true if it is one of the board's. */
    private boolean shortcutKey(KeyCode code) {

        switch (code) {
            case V -> board.paste(viewport.centre());
            case PLUS, EQUALS, ADD -> viewport.zoomIn();
            case MINUS, SUBTRACT -> viewport.zoomOut();
            case DIGIT0, NUMPAD0 -> viewport.actualSize();
            default -> {
                return false;
            }
        }

        return true;
    }

    /** {@code code} alone, or with any modifier: true if it is one of the board's. */
    private boolean boardKey(KeyCode code) {

        switch (code) {
            case DELETE, BACK_SPACE -> {
                return board.deleteSelected();
            }
            case ESCAPE -> board.select(null);
            default -> {
                return false;
            }
        }

        return true;
    }
}
