package it.infog.psiche.markdown.model;

/**
 * A note of a chapter as a reader finds it, to refer to again.
 *
 * @param label   its label, as written
 * @param number  the number readers see it by: the notes with references in the order of their first one, then the
 *                notes nothing refers to yet, in the order they are written
 * @param text    its text, as the note dialog shows it
 * @param summary its first words as a reader reads them, without the Markdown, to tell it from the others
 */
public record NumberedNote(String label,
                           int number,
                           String text,
                           String summary) {
}
