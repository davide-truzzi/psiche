package it.infog.psiche.export.model;

import org.commonmark.node.Node;

/**
 * A chapter ready to export as part of the book.
 *
 * @param id       an id from the chapter's place in the book, such as {@code chapter-3}, which every format uses to
 *                 mark where the chapter starts, so links can lead to it
 * @param title    the title a reader knows the chapter by: its first heading, or its file name if it has none
 * @param document the chapter's Markdown, parsed
 */
public record ExportedChapter(String id,
                              String title,
                              Node document) {
}
