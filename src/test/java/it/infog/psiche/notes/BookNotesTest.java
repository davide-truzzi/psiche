package it.infog.psiche.notes;

import it.infog.psiche.notes.model.NoteLayout;
import it.infog.psiche.notes.model.enums.NoteColor;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** A book's {@code notes/} in a temporary folder, for the tests of {@link NoteStore} and {@link DetachedNoteStore}. */
abstract class BookNotesTest {

    static final LocalDateTime MORNING = LocalDateTime.of(2026, 11, 1, 9, 30, 0);
    static final NoteLayout LAYOUT = new NoteLayout(40, 60, 220, 160, 1, NoteColor.PAPER);

    @TempDir
    Path tempDir;

    NoteStore store(LocalDateTime now) {
        return new NoteStore(notesDir(), Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    Path notesDir() {
        return tempDir.resolve("book/notes");
    }

    static NoteLayout layout(int order) {
        return new NoteLayout(0, 0, 220, 160, order, NoteColor.PAPER);
    }
}
