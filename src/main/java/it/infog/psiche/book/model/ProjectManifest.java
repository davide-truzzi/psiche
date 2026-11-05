package it.infog.psiche.book.model;

import java.time.LocalDateTime;

/**
 * What a Psiche project file says about the book it holds.
 *
 * @param folder   the name of the book's folder, which an import gives the book again
 * @param title    the book's title when it was exported
 * @param book     the book's identifier, such as a {@code urn:uuid}; blank for a book without one
 * @param exported when it was exported, in the exporting computer's time
 */
public record ProjectManifest(String folder,
                              String title,
                              String book,
                              LocalDateTime exported) {
}
