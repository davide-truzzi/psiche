package it.infog.psiche.export.model;

/**
 * A chapter as read from the book, before it is exported.
 *
 * @param fileTitle the chapter's file name without its extension, such as {@code 07-ritorno}
 * @param markdown  the chapter's text as saved
 */
public record ChapterText(String fileTitle,
                          String markdown) {

    /** True if the chapter has nothing to show: its file is empty or holds only blank lines. */
    public boolean isEmpty() {

        return markdown.isBlank();
    }
}
