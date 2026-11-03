package it.infog.psiche.spelling.model;

import it.infog.psiche.markdown.model.TextRange;

/**
 * A word of a chapter, as it is written, and where.
 *
 * @param text  the word, as it is in the chapter
 * @param range where it is in the chapter's Markdown
 */
public record Word(String text,
                   TextRange range) {
}
