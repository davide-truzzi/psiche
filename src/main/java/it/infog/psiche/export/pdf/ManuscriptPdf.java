package it.infog.psiche.export.pdf;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.model.Contents;

import java.nio.file.Path;

import static it.infog.psiche.export.XmlText.escape;

/**
 * Lays out XHTML as a manuscript PDF through OpenHTMLtoPDF: A4, double-spaced, with the authors, the book's title and
 * the page number at the top of every page, and Libertinus Serif embedded. The look is in {@code manuscript.css}; the
 * whole book adds {@code book.css}, for its title page and the page break before every chapter.
 */
public final class ManuscriptPdf {

    private final String chapterStyle;
    private final String bookStyle;

    public ManuscriptPdf() {

        this.chapterStyle = PdfLayout.readResource(ManuscriptPdf.class, "manuscript.css");
        this.bookStyle = chapterStyle + PdfLayout.readResource(ManuscriptPdf.class, "book.css");
    }

    /**
     * Writes one chapter as a manuscript PDF.
     *
     * @param title         the PDF's title, as a PDF reader shows it in the document's properties
     * @param header        the text at the top left of every page, such as the authors and the book's title
     * @param book          the book, for the PDF's author and language
     * @param body          the chapter's XHTML
     * @param baseDirectory the directory relative links and images resolve against, the book's chapters
     */
    public void writeChapter(String title, String header, BookProperties book, String body, Path baseDirectory,
                             Path target) {

        write(chapterStyle, title, header, book, "", body, baseDirectory, target);
    }

    /**
     * Writes the whole book as a manuscript PDF. Its first page is the title page, and its second the contents, both
     * without a header; each chapter is a {@code <section class="chapter">}, starting on a new page, and the pages are
     * numbered from the first chapter. The contents are also the PDF's bookmarks, which PDF readers list beside the
     * pages.
     *
     * @param contents the book's contents, for the bookmarks
     * @param body     the title page's {@code <section class="title-page">}, the contents' {@code <nav class="toc">},
     *                 then the chapters' sections, each with its id
     * @see #writeChapter
     */
    public void writeBook(String title, String header, BookProperties book, Contents contents, String body,
                          Path baseDirectory, Path target) {

        write(bookStyle, title, header, book, bookmarks(contents), body, baseDirectory, target);
    }

    /** @param head more for the page's head, such as the bookmarks; empty for none */
    private void write(String stylesheet, String title, String header, BookProperties book, String head, String body,
                       Path baseDirectory, Path target) {

        String style = stylesheet.replace("{{header}}", PdfLayout.cssString(header));
        PdfLayout.write(title, book, style, head, body, baseDirectory, target);
    }

    /** The PDF's bookmarks, as OpenHTMLtoPDF reads them from the head: the contents, then a bookmark per chapter. */
    private static String bookmarks(Contents contents) {

        StringBuilder bookmarks = new StringBuilder("<bookmarks>\n");
        bookmarks.append("<bookmark name=\"%s\" href=\"#toc\"/>\n".formatted(escape(contents.heading())));
        for (Contents.Entry entry : contents.entries())
            bookmarks.append("<bookmark name=\"%s\" href=\"#%s\"/>\n"
                    .formatted(escape(entry.title()), escape(entry.id())));

        return bookmarks.append("</bookmarks>\n").toString();
    }
}
