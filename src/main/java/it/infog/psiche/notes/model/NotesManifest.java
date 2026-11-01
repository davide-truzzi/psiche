package it.infog.psiche.notes.model;

/**
 * What an export of notes says about itself, so an import can tell whose notes it holds.
 *
 * @param book  the book's identifier, such as a {@code urn:uuid}; blank for a book without one
 * @param title the book's title when the notes were exported
 */
public record NotesManifest(String book,
                            String title) {
}
