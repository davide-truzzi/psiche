package it.infog.psiche.notes.model;

import it.infog.psiche.notes.model.enums.OrphanCause;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Notes in a folder of {@code notes/} that belongs to no chapter of the book.
 *
 * @param folder    the folder that holds them
 * @param chapter   the chapter they belonged to, by name
 * @param cause     how they were left without a chapter
 * @param deletedAt when Psiche deleted them or their chapter; empty for {@link OrphanCause#CHAPTER_GONE}
 * @param notes     the notes
 */
public record OrphanGroup(Path folder,
                          String chapter,
                          OrphanCause cause,
                          Optional<LocalDateTime> deletedAt,
                          List<Note> notes) {

    public OrphanGroup {

        notes = List.copyOf(notes);
    }
}
