package it.infog.psiche.notes.model;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A note on a chapter's board, as saved.
 *
 * @param id                the note's id, unique across books and computers, and the name of its folder
 * @param text              what the author wrote, plain text
 * @param layout            where it sits on the board and how it looks
 * @param duplicateOnImport true if an import added it beside a version of it changed on this computer too
 * @param attachment        the file it carries, if any
 * @param pin               the paragraph of its chapter it is pinned to, if any
 */
public record Note(String id,
                   String text,
                   NoteLayout layout,
                   boolean duplicateOnImport,
                   Optional<Path> attachment,
                   Optional<NotePin> pin) {
}
