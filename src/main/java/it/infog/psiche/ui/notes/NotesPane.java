package it.infog.psiche.ui.notes;

import it.infog.psiche.book.BookState;
import it.infog.psiche.book.model.enums.NotesBoard;
import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.notes.model.BoardView;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.notes.card.NoteCard;

import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * The Notes tab: the open chapter's {@link NoteBoard}, or the whole book's, as the {@link BoardSwitch} above it says,
 * shown in a {@link BoardViewport}, the author moving around it with the pointer, the wheel, the keys or the corner
 * bar; and a message while there is no note on the board yet. The board last looked at, the chapter's or the book's, is
 * remembered for each book; a book with no chapter yet shows its own. The files' copying and pictures are
 * {@link NoteFiles}'.
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
    private final BoardCounts counts = new BoardCounts();
    private final BoardSwitch boardSwitch = new BoardSwitch(counts);
    private final BoardViewport viewport;
    private final Label message = new Label();
    private final BoardBar bar;
    private final ReadOnlyBooleanWrapper available = new ReadOnlyBooleanWrapper();
    private final BooleanProperty showing = new SimpleBooleanProperty();
    private final NoteFiles files;
    private final NotesThread notes;

    private boolean loaded;
    // True while the book is exported: the notes can be looked at, but not changed.
    private boolean frozen;
    // The open chapter, whose board the switch's left half shows; null with none open.
    private String chapter;
    // The board the author chose last, kept in the book's state; the book's is shown anyway while no chapter is open.
    private NotesBoard chosen = NotesBoard.CHAPTER;
    private BookState state;
    // True while the switch is set to the board shown, not by the author.
    private boolean settingSwitch;

    /** What a chapter's board shows when it opens. */
    private record Loaded(List<Note> notes,
                          BoardView view) {
    }

    /** What the board not on screen holds, for the tab's title and the switch. */
    private record Summary(int count,
                           boolean duplicates) {
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

        //the switch above the board
        StackPane boardArea = new StackPane(viewport, message, bar);
        VBox.setVgrow(boardArea, Priority.ALWAYS);
        boardSwitch.selectedProperty().addListener((obs, old, board) -> choose(board));

        getChildren().add(new VBox(boardSwitch, boardArea));
        getStyleClass().add("notes-pane");
        BoardKeys keys = new BoardKeys(board, viewport);
        addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (!frozen)
                keys.pressed(event);
        });

        showing.addListener((obs, was, isShowing) -> {
            if (isShowing)
                board.showPictures();
        });
        board.countProperty().addListener((obs, old, count) -> {
            updateMessage();
            countShown();
        });
        board.duplicatesProperty().addListener((obs, had, has) -> countShown());
        updateMessage();
    }

    /** Whether the tab is on screen: pictures are made only while it is. Bind it to the tab's selection. */
    public BooleanProperty showingProperty() {

        return showing;
    }

    /** The tab's title, as {@link BoardCounts#title} gives it: "Notes (2+3)", the chapter's notes then the book's. */
    public StringBinding titleBinding() {

        return counts.title();
    }

    /** True while a note on the chapter's board or the book's carries the Duplicate on Import tag. */
    public ObservableBooleanValue duplicatesProperty() {

        return counts.anyDuplicates();
    }

    /** True while a book is open; the tab is disabled otherwise. */
    public ReadOnlyBooleanProperty availableProperty() {

        return available.getReadOnlyProperty();
    }

    /**
     * Keeps the notes from changing while {@code frozen}, as while the book is exported. The pane itself is not
     * disabled, as its tab enables and disables it as the tab is.
     */
    public void setFrozen(boolean frozen) {

        this.frozen = frozen;
        viewport.setDisable(frozen);
        bar.setDisable(frozen);
        boardSwitch.setDisable(frozen);
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
     * Takes the open book's title for the switch, and its state, where the board looked at last is remembered; both
     * {@code null} as the book closes.
     */
    public void setBook(String title, BookState state) {

        this.state = state;
        chosen = state == null ? NotesBoard.CHAPTER : state.notesBoard();
        boardSwitch.setBook(title);
        counts.clear();
    }

    /** Names the switch's right half after {@code title}, the book's new title. */
    public void setBookTitle(String title) {

        boardSwitch.setBook(title);
    }

    /**
     * Shows the board chosen for {@code chapter} in {@code store}, the chapter's or the book's, saving first what the
     * board it replaces still had to save.
     *
     * @param store   the open book's notes, or {@code null} with no book open
     * @param chapter the open chapter's name, or {@code null} with none open
     */
    public void show(NoteStore store, String chapter) {

        this.chapter = chapter;
        boardSwitch.setChapter(chapter);
        showBoard(store);
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
        countHidden(notes.store());
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

    // Boards

    /** Shows the board the author chose, after the switch; nothing changes while the board is only set to it. */
    private void choose(NotesBoard board) {

        if (settingSwitch)
            return;

        chosen = board;
        if (state != null)
            state.saveNotesBoard(board);
        showBoard(notes.store());
    }

    /** Shows the board chosen, the book's while no chapter is open, and counts the other one. */
    private void showBoard(NoteStore store) {

        NotesBoard shown = shownBoard();
        settingSwitch = true;
        boardSwitch.select(shown);
        settingSwitch = false;

        String folder = store == null ? null : folder(shown);
        if (!notes.shows(store, folder))
            open(store, folder);
        countHidden(store);
    }

    /** The board on screen: the one chosen, or the book's while no chapter is open. */
    private NotesBoard shownBoard() {

        return chapter == null ? NotesBoard.BOOK : chosen;
    }

    /** The folder {@code board}'s notes are kept in, by the name the notes are asked for; null with no chapter open. */
    private String folder(NotesBoard board) {

        return board == NotesBoard.BOOK ? NoteStore.BOOK : chapter;
    }

    /** Tells the counts what the board on screen holds, as it changes. */
    private void countShown() {

        counts.set(shownBoard(), board.countProperty().get(), board.duplicatesProperty().get());
    }

    /** Reads what the board not on screen holds, for the tab's title and the switch. */
    private void countHidden(NoteStore store) {

        NotesBoard hidden = shownBoard() == NotesBoard.BOOK ? NotesBoard.CHAPTER : NotesBoard.BOOK;
        String folder = folder(hidden);
        if (store == null || folder == null) {
            counts.set(hidden, 0, false);
            return;
        }

        notes.read("Cannot count the notes of " + folder, (shownStore, shownFolder) -> summary(store.notes(folder)),
                summary -> counts.set(hidden, summary.count(), summary.duplicates()));
    }

    private static Summary summary(List<Note> read) {

        return new Summary(read.size(), read.stream().anyMatch(Note::duplicateOnImport));
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
}
