package it.infog.psiche.export.model;

/**
 * Where an internal link leads in an exported file.
 *
 * @param chapterId the id of the chapter it leads to, such as {@code chapter-3}, which an ebook's page is named after
 * @param elementId the id of the place it leads to: the chapter's own for its start, or a heading's, such as
 *                  {@code chapter-3--the-storm}
 */
public record LinkPlace(String chapterId,
                        String elementId) {
}
