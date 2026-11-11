package it.infog.psiche.ui.notes;

import it.infog.psiche.book.model.enums.NotesBoard;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.notes.card.NoteCard;
import it.infog.psiche.ui.notes.card.NotePinActions;

import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyEvent;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * The pins of the notes on the open chapter's board, which the editor keeps on their paragraphs ({@link NotePinning}):
 * the pin's actions in a note's menu, the pin a pinned note shows, and the note shown in the middle of the view, its
 * border darker, when the author clicks its pin in the editor's margin, until they next type.
 */
public final class BoardPins implements NotePinActions {

    private static final String PINNED = "Note pinned to paragraph %d";
    private static final String UNPINNED = "Note unpinned";
    private static final String TAKEN = "This paragraph has a pinned note";
    private static final String TAKEN_TEXT = "This paragraph has the note \"%s\" pinned. Pin this one instead?";

    private final NoteBoard board;
    private final BoardViewport viewport;
    private final BoardSwitch boardSwitch;
    private final StatusBar statusBar;
    // True while the open chapter's board is shown with its notes read.
    private final BooleanSupplier ready;
    // Shows the Notes tab.
    private Runnable reveal = () -> {};
    private final EventHandler<KeyEvent> typed = event -> endFound();
    // None until the editor gives its pins.
    private NotePinning pinning;
    // The note to show once its board is ready, and the note shown, until the author types; null with none.
    private String wanted;
    private NoteCard found;
    private Scene listened;

    BoardPins(NoteBoard board, BoardViewport viewport, BoardSwitch boardSwitch, StatusBar statusBar,
              BooleanSupplier ready) {

        this.board = board;
        this.viewport = viewport;
        this.boardSwitch = boardSwitch;
        this.statusBar = statusBar;
        this.ready = ready;
    }

    /** Runs {@code reveal} to show the Notes tab, as a note is shown by a click on its pin in the editor. */
    public void setOnReveal(Runnable reveal) {

        this.reveal = reveal;
    }

    /** The editor's pins of the open chapter, which the notes on its board are shown and changed by. */
    public void setPinning(NotePinning pinning) {

        this.pinning = pinning;
        refresh();
    }

    /** Shows the pin on each pinned note of the board, as the pins changed. */
    public void refresh() {

        board.cards().forEach(card -> card.setPinned(active() && pinning.isPinned(card.id())));
    }

    /**
     * Shows the note in the middle of the open chapter's board, in the Notes tab, its border darker until the author
     * types: at once if the board is ready, or once it is.
     */
    public void show(String id) {

        wanted = id;
        reveal.run();
        // The chapter's board, if the book's was shown: the switch opens it.
        boardSwitch.select(NotesBoard.CHAPTER);
        showWanted();
    }

    /** The text of the note, as typed on the open chapter's board; empty if the board does not show it. */
    public Optional<String> text(String id) {

        return active() ? card(id).map(NoteCard::text) : Optional.empty();
    }

    /** What the board's notes can do with their pins: nothing on the book's board, or before the editor gives them. */
    Optional<NotePinActions> forCards() {

        return active() ? Optional.of(this) : Optional.empty();
    }

    /** Shows the pins of the notes just read, and the note waiting to be shown, if it is among them. */
    void loaded() {

        refresh();
        showWanted();
    }

    /** Takes the pin of a note leaving the open chapter's board, moved or detached. */
    void left(NoteCard card) {

        if (card == found)
            endFound();
        if (active())
            pinning.unpin(card.id());
    }

    // A note's pin, from its menu

    @Override
    public boolean canPinHere() {

        return pinning.canPinHere();
    }

    @Override
    public boolean isPinned(NoteCard card) {

        return pinning.isPinned(card.id());
    }

    @Override
    public void pinHere(NoteCard card) {

        Optional<String> other = pinning.pinnedHere().filter(id -> !id.equals(card.id()));
        if (other.isPresent() && !replaces(other.get()))
            return;

        pinning.pinHere(card.id()).ifPresent(paragraph -> statusBar.info(PINNED.formatted(paragraph)));
    }

    @Override
    public void goTo(NoteCard card) {

        pinning.goTo(card.id());
    }

    @Override
    public void unpin(NoteCard card) {

        pinning.unpin(card.id());
        statusBar.info(UNPINNED);
    }

    // Helpers

    private boolean active() {

        return pinning != null && ready.getAsBoolean();
    }

    private Optional<NoteCard> card(String id) {

        return board.cards().stream().filter(card -> card.id().equals(id)).findFirst();
    }

    /** True if the author chooses to pin the note instead of {@code other}, pinned to the paragraph now. */
    private boolean replaces(String other) {

        String name = card(other).map(NoteCard::text).map(NotePinning::label).orElse(NotePinning.label(""));
        ButtonType pin = new ButtonType("Pin", ButtonData.OK_DONE);
        return Dialogs.choose(board.getScene().getWindow(), TAKEN, TAKEN_TEXT.formatted(name), pin, ButtonType.CANCEL)
                .filter(pin::equals).isPresent();
    }

    /** Centres the note waiting to be shown and marks it found, once the board is ready; forgets it if it is gone. */
    private void showWanted() {

        if (wanted == null || !active())
            return;

        Optional<NoteCard> card = card(wanted);
        wanted = null;
        card.ifPresent(this::markFound);
    }

    private void markFound(NoteCard card) {

        endFound();
        viewport.centreOn(card.noteLayout());
        found = card;
        card.setFound(true);
        listened = board.getScene();
        if (listened != null)
            listened.addEventFilter(KeyEvent.KEY_TYPED, typed);
    }

    private void endFound() {

        if (found != null)
            found.setFound(false);
        found = null;
        if (listened != null)
            listened.removeEventFilter(KeyEvent.KEY_TYPED, typed);
        listened = null;
    }
}
