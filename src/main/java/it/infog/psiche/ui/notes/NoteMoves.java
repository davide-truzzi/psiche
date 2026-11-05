package it.infog.psiche.ui.notes;

import it.infog.psiche.book.model.enums.NotesBoard;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.notes.card.NoteCard;
import it.infog.psiche.ui.notes.card.NoteMoveActions;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The notes leaving the board for another: another chapter's, picked from a list, or the other board the Notes tab
 * shows, from the note's menu or by dropping it on that board's half of the {@link BoardSwitch}. A note moves with its
 * file, to the top of the other board's stack, at the place it had here; text typed a moment ago is saved first.
 */
final class NoteMoves implements NoteMoveActions {

    /** A board a note can go to straight away: which of the two, the folder its notes are kept in, and its name. */
    record Destination(NotesBoard board,
                       String folder,
                       String name) {
    }

    private final NoteBoard board;
    private final NotesThread notes;
    private final StatusBar statusBar;
    // Reads the board's notes again, as a note could not leave it.
    private final Runnable reload;

    // The book's chapters, by name, which a note can move to.
    private Supplier<List<String>> chapters = List::of;
    private Supplier<Optional<Destination>> other = Optional::empty;
    private BoardSwitch boardSwitch;
    // Runs once a note has reached another board, so it is counted there.
    private Runnable moved = () -> {};

    NoteMoves(NoteBoard board, NotesThread notes, StatusBar statusBar, Runnable reload) {

        this.board = board;
        this.notes = notes;
        this.statusBar = statusBar;
        this.reload = reload;
    }

    /** @param chapters gives the book's chapters, by name, in their order */
    void setChapters(Supplier<List<String>> chapters) {

        this.chapters = chapters;
    }

    /**
     * @param other       gives the other board the Notes tab shows, if there is one
     * @param boardSwitch where a dragged note can be dropped on that board's half
     * @param moved       runs once a note has reached another board
     */
    void setOtherBoard(Supplier<Optional<Destination>> other, BoardSwitch boardSwitch, Runnable moved) {

        this.other = other;
        this.boardSwitch = boardSwitch;
        this.moved = moved;
    }

    @Override
    public boolean hasOtherChapters() {

        return !otherChapters().isEmpty();
    }

    @Override
    public void moveToChapter(NoteCard card) {

        List<String> others = otherChapters();
        if (others.isEmpty())
            return;

        // The chapter after this one, or the one before the last: where a note is most likely to go.
        int next = Math.max(0, chapters.get().indexOf(notes.chapter()));
        String suggested = next < others.size() ? others.get(next) : others.getLast();
        Dialogs.chooseChapter(board.getScene().getWindow(), "Move the note to another chapter", others, suggested,
                "Move").ifPresent(chapter -> moveTo(card, chapter, chapter));
    }

    @Override
    public Optional<String> otherBoard() {

        return other.get().map(Destination::name);
    }

    @Override
    public void moveToOtherBoard(NoteCard card) {

        other.get().ifPresent(destination -> moveTo(card, destination.folder(), destination.name()));
    }

    @Override
    public void dragged(NoteCard card, double sceneX, double sceneY) {

        other.get().ifPresent(destination -> boardSwitch.dragOver(destination.board(), sceneX, sceneY));
    }

    @Override
    public boolean dropped(NoteCard card, double sceneX, double sceneY) {

        Optional<Destination> destination = other.get();
        if (destination.isEmpty() || !boardSwitch.dropAt(destination.get().board(), sceneX, sceneY))
            return false;

        moveTo(card, destination.get().folder(), destination.get().name());
        return true;
    }

    /** Moves the note of {@code card} to the board kept in {@code folder}, named {@code name} for the author. */
    private void moveTo(NoteCard card, String folder, String name) {

        // Text typed a moment ago is saved first: the thread does one thing at a time, in order.
        card.flushText();
        board.remove(card);
        notes.write("Cannot move the note", (store, chapter) -> store.moveNote(chapter, card.id(), folder), () -> {
            statusBar.info("Note moved to " + name);
            moved.run();
        }, reload);
    }

    /** The book's chapters but the board's, in their order; all of them from the book's board. */
    private List<String> otherChapters() {

        String shown = notes.chapter();
        return chapters.get().stream().filter(chapter -> !chapter.equals(shown)).toList();
    }
}
