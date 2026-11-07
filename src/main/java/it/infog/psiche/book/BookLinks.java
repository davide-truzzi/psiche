package it.infog.psiche.book;

import it.infog.psiche.markdown.TextLinks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** The links between a book's chapters, kept leading where they did when a chapter is renamed. */
public final class BookLinks {

    private static final Logger log = LoggerFactory.getLogger(BookLinks.class);

    private BookLinks() {
    }

    /**
     * Rewrites the links of every chapter of {@code book} to the chapter named {@code from}, just renamed {@code to},
     * so they lead to it under its new name, and saves the chapters that change. A chapter that cannot be read or
     * written is left as it was, and logged: its links to the chapter show as broken.
     *
     * @return the chapters rewritten, in the book's order
     */
    public static List<Chapter> followRename(BookFolder book, String from, String to) {

        List<Chapter> rewritten = new ArrayList<>();
        for (Chapter chapter : book.chapters()) {
            try {
                String text = book.read(chapter);
                String relinked = TextLinks.withChapter(text, from, to);
                if (!relinked.equals(text)) {
                    book.write(chapter, relinked);
                    rewritten.add(chapter);
                }
            } catch (UncheckedIOException e) {
                log.warn("Cannot follow the rename of {} in {}", from, chapter.title(), e);
            }
        }

        return rewritten;
    }
}
