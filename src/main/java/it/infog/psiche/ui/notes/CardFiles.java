package it.infog.psiche.ui.notes;

import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;

import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.input.Clipboard;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The files on the board's cards: giving a note a file, dropped, chosen or pasted, and the card grown to show it;
 * taking it away, to Detached notes; opening it or its folder; and asking {@link NoteFiles} for its picture, once,
 * while the tab is showing.
 */
final class CardFiles {

    static final String NOTHING_TO_PASTE = "Nothing to paste: copy a file or an image first";

    // A short note given a file grows to this height, to show the file.
    private static final double WITH_FILE_HEIGHT = 250;

    private final NoteBoard board;
    private final NoteFiles files;
    private final NotesThread notes;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final ObservableBooleanValue showing;
    // The cards whose picture has been asked for, so it is made once.
    private final Set<NoteCard> pictured = new HashSet<>();

    /** @param showing whether the tab is on screen: pictures are made only while it is */
    CardFiles(NoteBoard board, NoteFiles files, NotesThread notes, StatusBar statusBar, ErrorReporter errors,
              ObservableBooleanValue showing) {

        this.board = board;
        this.files = files;
        this.notes = notes;
        this.statusBar = statusBar;
        this.errors = errors;
        this.showing = showing;
    }

    /** Copies {@code file} into the note, in place of the file it carried. */
    void attach(NoteCard card, Path file) {

        if (!notes.hasBoard())
            return;

        statusBar.info("Copying " + file.getFileName() + "…");
        files.attach(notes.store(), notes.chapter(), card, file, copy -> attached(card, copy),
                failure -> attachFailed(card, file.getFileName().toString(), failure));
    }

    /** Asks the author for a file, and copies it into the note. */
    void chooseFile(NoteCard card) {

        FileChooser chooser = new FileChooser();
        chooser.setTitle(card.attachment().isPresent() ? "Replace the note's file" : "Attach a file to the note");
        File chosen = chooser.showOpenDialog(board.getScene().getWindow());
        if (chosen != null)
            attach(card, chosen.toPath());
    }

    /** Copies the file, or the image, on the clipboard into the note. */
    void paste(NoteCard card) {

        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles() && !clipboard.getFiles().isEmpty())
            attach(card, clipboard.getFiles().getFirst().toPath());
        else if (clipboard.hasImage() && notes.hasBoard())
            files.attachImage(notes.store(), notes.chapter(), card, clipboard.getImage(), copy -> attached(card, copy),
                    failure -> attachFailed(card, "the image", failure));
        else
            statusBar.info(NOTHING_TO_PASTE);
    }

    void open(NoteCard card) {

        card.attachment().filter(Files::exists).ifPresent(files::open);
    }

    void showInFolder(NoteCard card) {

        card.attachment().ifPresent(files::showFolder);
    }

    /** Takes the file away from the note, to Detached notes. */
    void remove(NoteCard card) {

        card.setAttachment(Optional.empty());
        pictured.remove(card);
        notes.write("Cannot remove the note's file", (store, chapter) -> store.removeAttachment(chapter, card.id()),
                board::binned);
        statusBar.info("File removed: it's in Detached notes until you remove it for good");
    }

    /** Asks for the picture of the card's file, once, while the tab is showing. */
    void picture(NoteCard card) {

        if (showing.get() && card.wantsPicture() && pictured.add(card))
            files.showPicture(card);
    }

    /** Forgets the pictures asked for, as the board is emptied. */
    void clear() {

        pictured.clear();
    }

    /** Shows the file now in the note, the card grown to show it if it was short, and its picture. */
    private void attached(NoteCard card, Path copy) {

        if (card.getParent() != board)
            return;

        // The file it replaced, if any, went to Detached notes.
        boolean replaced = card.attachment().isPresent();
        card.setAttachment(Optional.of(copy));
        repicture(card);
        if (card.getPrefHeight() < WITH_FILE_HEIGHT) {
            card.setPrefHeight(Math.min(WITH_FILE_HEIGHT, BoardViewport.BOARD_HEIGHT - card.getLayoutY()));
            board.layoutChanged(card);
        }
        statusBar.info("Attached " + copy.getFileName());
        if (replaced)
            board.binned();
    }

    private void attachFailed(NoteCard card, String name, Throwable failure) {

        // The card shows again the file it had, if any.
        card.setAttachment(card.attachment());
        repicture(card);
        errors.report("Cannot attach " + name, failure);
    }

    private void repicture(NoteCard card) {

        pictured.remove(card);
        picture(card);
    }
}
