package it.infog.psiche.ui.notes.card;

import java.nio.file.Path;

/** What a card can do with the file its note carries, as its board does it. */
public interface NoteFileActions {

    /** Copies {@code file} into the note, in place of the file it carried. */
    void attach(NoteCard card, Path file);

    /** Asks the author for a file, and copies it into the note. */
    void chooseFile(NoteCard card);

    /** Copies the file, or the image, on the clipboard into the note. */
    void paste(NoteCard card);

    /** Opens the note's file in the system's default program. */
    void open(NoteCard card);

    /** Opens the note's folder, where its file is, in the system's file manager. */
    void showInFolder(NoteCard card);

    /** Takes the file away from the note, to Detached notes. */
    void remove(NoteCard card);
}
