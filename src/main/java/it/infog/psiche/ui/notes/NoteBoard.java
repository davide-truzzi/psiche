package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NoteLayout;
import it.infog.psiche.notes.model.enums.NoteColor;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.notes.card.NoteCard;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * The open chapter's notes, as cards on the board {@link BoardViewport} shows: which one is selected, the order they
 * lie in, and how many there are. A double-click on an empty spot, or New note, adds a note; files dropped on an empty
 * spot, or pasted on the board, make a note each. Every change a card reports is written on the {@link NotesThread};
 * the cards' files are {@link CardFiles}'.
 */
final class NoteBoard extends Pane implements NoteCard.Board {

    private static final double NEW_WIDTH = 220;
    private static final double NEW_HEIGHT = 160;
    // A new note on a taken spot moves this far down and right, until the spot is free.
    private static final double NEW_OFFSET = 24;
    private static final String CANNOT_SAVE = "Cannot save the note";

    private final NotesThread notes;
    private final StatusBar statusBar;
    private final NotesPane.FirstNoteListener firstNote;
    private final CardFiles files;
    private final ReadOnlyIntegerWrapper count = new ReadOnlyIntegerWrapper();
    private final ReadOnlyBooleanWrapper duplicates = new ReadOnlyBooleanWrapper();
    // The board's one right-click menu, so two are never open at once.
    private final ContextMenu menu = new ContextMenu();

    // Runs once something has gone to Detached notes: a note, or a file the note no longer carries.
    private Runnable onBinned = () -> {};
    private NoteCard selected;
    private int topOrder;

    /** A note just made, and whether it is the book's first. */
    private record Created(Note note,
                           boolean first) {
    }

    /** @param showing whether the tab is on screen: pictures are made only while it is */
    NoteBoard(NotesThread notes, NoteFiles noteFiles, StatusBar statusBar, ErrorReporter errors,
              ObservableBooleanValue showing, NotesPane.FirstNoteListener firstNote) {

        this.notes = notes;
        this.statusBar = statusBar;
        this.firstNote = firstNote;
        this.files = new CardFiles(this, noteFiles, notes, statusBar, errors, showing);

        getStyleClass().add("notes-board");
        setOnMouseClicked(this::createAtPointer);
        setOnDragOver(event -> {
            if (event.getDragboard().hasFiles())
                event.acceptTransferModes(TransferMode.COPY);
        });
        setOnDragDropped(this::dropOnBoard);
    }

    ReadOnlyIntegerProperty countProperty() {

        return count.getReadOnlyProperty();
    }

    ReadOnlyBooleanProperty duplicatesProperty() {

        return duplicates.getReadOnlyProperty();
    }

    void setOnBinned(Runnable onBinned) {

        this.onBinned = onBinned;
    }

    /** Tells that something has gone to Detached notes. */
    void binned() {

        onBinned.run();
    }

    List<NoteCard> cards() {

        return getChildren().stream().map(NoteCard.class::cast).toList();
    }

    /** Lays the notes read on the empty board, each in its place and order. */
    void show(List<Note> read) {

        read.forEach(note -> getChildren().add(new NoteCard(note, this)));
        showPictures();
        topOrder = read.stream().mapToInt(note -> note.layout().order()).max().orElse(0);
        updateCounts();
    }

    void clear() {

        getChildren().clear();
        files.clear();
        selected = null;
        topOrder = 0;
        updateCounts();
    }

    /** Asks for the pictures of the cards' files not made yet, as the tab shows. */
    void showPictures() {

        cards().forEach(files::picture);
    }

    void select(NoteCard card) {

        if (selected != null)
            selected.setSelected(false);
        selected = card;
        if (card != null)
            card.setSelected(true);
    }

    /** Moves the selected note to Detached notes: false if none is selected. */
    boolean deleteSelected() {

        if (selected == null)
            return false;

        delete(selected);
        return true;
    }

    /** A note in the middle of the part of the board in view, around {@code centre}. */
    void createAround(Point2D centre) {

        create(centre.getX() - NEW_WIDTH / 2, centre.getY() - NEW_HEIGHT / 2, NoteCard::focusText);
    }

    /**
     * Pastes what the clipboard holds into the selected note; with none selected, makes a note for each file, or for
     * the image, around {@code centre}.
     */
    void paste(Point2D centre) {

        if (selected != null) {
            files.paste(selected);
            return;
        }

        Clipboard clipboard = Clipboard.getSystemClipboard();
        double x = centre.getX() - NEW_WIDTH / 2;
        double y = centre.getY() - NEW_HEIGHT / 2;

        if (clipboard.hasFiles() && !clipboard.getFiles().isEmpty())
            createForFiles(clipboard.getFiles(), x, y);
        else if (clipboard.hasImage())
            create(x, y, files::paste);
        else
            statusBar.info(CardFiles.NOTHING_TO_PASTE);
    }

    // What the cards report

    @Override
    public void bringToFront(NoteCard card) {

        select(card);
        card.toFront();
        if (card.noteLayout().order() == topOrder)
            return;

        card.setOrder(++topOrder);
        layoutChanged(card);
    }

    @Override
    public void layoutChanged(NoteCard card) {

        NoteLayout layout = card.noteLayout();
        notes.write(CANNOT_SAVE, (store, chapter) -> store.saveLayout(chapter, card.id(), layout));
    }

    @Override
    public void textChanged(NoteCard card, String text) {

        notes.write(CANNOT_SAVE, (store, chapter) -> store.saveText(chapter, card.id(), text));
    }

    @Override
    public void delete(NoteCard card) {

        getChildren().remove(card);
        if (card == selected)
            selected = null;
        updateCounts();

        notes.write("Cannot delete the note", (store, chapter) -> store.binNote(chapter, card.id()), this::binned);
        statusBar.info("Note deleted: it's in Detached notes until you remove it for good");
    }

    @Override
    public void clearDuplicateTag(NoteCard card) {

        card.setDuplicateOnImport(false);
        updateCounts();
        notes.write(CANNOT_SAVE, (store, chapter) -> store.setDuplicateOnImport(chapter, card.id(), false));
    }

    @Override
    public void showMenu(NoteCard card, List<MenuItem> items, double screenX, double screenY) {

        menu.hide();
        menu.getItems().setAll(items);
        menu.show(card, screenX, screenY);
    }

    @Override
    public void attach(NoteCard card, Path file) {

        files.attach(card, file);
    }

    @Override
    public void chooseFile(NoteCard card) {

        files.chooseFile(card);
    }

    @Override
    public void paste(NoteCard card) {

        files.paste(card);
    }

    @Override
    public void openFile(NoteCard card) {

        files.open(card);
    }

    @Override
    public void showInFolder(NoteCard card) {

        files.showInFolder(card);
    }

    @Override
    public void removeFile(NoteCard card) {

        files.remove(card);
    }

    // New notes

    /**
     * Adds a note with its top-left corner at {@code x}, {@code y}, or the nearest free spot after it, then hands its
     * card to {@code then}: to put the caret in it, or to give it a file.
     */
    private void create(double x, double y, Consumer<NoteCard> then) {

        if (!notes.hasBoard())
            return;

        //a free spot on the board
        double left = Math.clamp(x, 0, BoardViewport.BOARD_WIDTH - NEW_WIDTH);
        double top = Math.clamp(y, 0, BoardViewport.BOARD_HEIGHT - NEW_HEIGHT);
        while (taken(left, top) && left + NEW_OFFSET <= BoardViewport.BOARD_WIDTH - NEW_WIDTH
                && top + NEW_OFFSET <= BoardViewport.BOARD_HEIGHT - NEW_HEIGHT) {
            left += NEW_OFFSET;
            top += NEW_OFFSET;
        }

        //the note, made on the notes thread, then shown
        NoteLayout layout = new NoteLayout(left, top, NEW_WIDTH, NEW_HEIGHT, ++topOrder, NoteColor.PAPER);
        notes.read("Cannot create the note", (store, chapter) -> {
            boolean first = store.isEmpty();
            return new Created(store.create(chapter, layout), first);
        }, created -> created(created, then));
    }

    /** Shows the note just made, selected, hands its card to {@code then}, and tells if it is the book's first. */
    private void created(Created created, Consumer<NoteCard> then) {

        NoteCard card = new NoteCard(created.note(), this);
        getChildren().add(card);
        updateCounts();
        select(card);
        then.accept(card);
        if (created.first())
            firstNote.firstNoteCreated();
    }

    private boolean taken(double x, double y) {

        return getChildren().stream()
                .anyMatch(card -> Math.abs(card.getLayoutX() - x) < 8 && Math.abs(card.getLayoutY() - y) < 8);
    }

    private void createAtPointer(MouseEvent event) {

        if (event.getTarget() != this || event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2)
            return;

        // The pointer lands on the new note's strip, near its grip.
        create(event.getX() - 20, event.getY() - 11, NoteCard::focusText);
    }

    /** Files dropped on an empty spot make a note each, fanned out from where they fell. */
    private void dropOnBoard(DragEvent event) {

        if (!event.getDragboard().hasFiles())
            return;

        // The pointer lands on the first note's strip, near its grip.
        createForFiles(event.getDragboard().getFiles(), event.getX() - 20, event.getY() - 11);
        event.setDropCompleted(true);
        event.consume();
    }

    /** A note for each of {@code given}, fanned out from {@code x}, {@code y}, each given its file. */
    private void createForFiles(List<File> given, double x, double y) {

        for (int i = 0; i < given.size(); i++) {
            Path file = given.get(i).toPath();
            create(x + i * NEW_OFFSET, y + i * NEW_OFFSET, card -> files.attach(card, file));
        }
    }

    private void updateCounts() {

        count.set(getChildren().size());
        duplicates.set(cards().stream().anyMatch(NoteCard::isDuplicateOnImport));
    }
}
