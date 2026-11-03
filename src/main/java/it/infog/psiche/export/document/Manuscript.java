package it.infog.psiche.export.document;

import it.infog.psiche.export.document.model.enums.PageStart;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ExportedChapter;

import org.commonmark.node.Node;

/**
 * What a word processor's document holds, in the same order for every format: a chapter alone, or the whole book,
 * laid out like the manuscript PDF. Each format only gives the {@link DocumentBody} to write it into, and packs it.
 */
public final class Manuscript {

    private Manuscript() {
    }

    /**
     * Writes one chapter.
     *
     * @param document the chapter's Markdown, parsed
     */
    public static void writeChapter(DocumentBody body, Node document, Pictures pictures) {

        document.accept(new ChapterRenderer(body, pictures));
    }

    /**
     * Writes the whole book: the title page and the contents, without the header, then every chapter on a new page,
     * the first one numbered page 1. Each chapter starts with a bookmark named after its id, which its line in the
     * contents links to.
     */
    public static void writeBook(DocumentBody body, BookExport book, Pictures pictures) {

        TitlePageRenderer.write(body, book.titlePage(), book.titlePage().cover().flatMap(pictures::cover));
        ContentsRenderer.write(body, book.contents());

        //chapters
        for (ExportedChapter chapter : book.chapters()) {
            boolean first = chapter == book.chapters().getFirst();
            body.startPage(first ? PageStart.FIRST_CHAPTER : PageStart.CHAPTER, chapter.id());
            writeChapter(body, chapter.document(), pictures);
        }
    }
}
