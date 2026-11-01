package it.infog.psiche.export;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.odt.OdtWriter;
import it.infog.psiche.export.pdf.ManuscriptPdf;
import org.commonmark.parser.Parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a chapter to a file outside the book, as Markdown or as a manuscript PDF or OpenDocument text. */
public final class ChapterExporter {

    private static final Parser PARSER = Parser.builder().build();

    private final ManuscriptPdf pdf = new ManuscriptPdf();
    private final OdtWriter odt = new OdtWriter();

    /** Writes the chapter's Markdown as it is; links to the book's media stay relative to the book's chapters. */
    public void markdown(String markdown, Path target) {

        try {
            Files.writeString(target, markdown);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + target, e);
        }
    }

    /**
     * Writes the chapter as a manuscript PDF: A4, double-spaced, with the authors, the book's title and the page number
     * in the header of every page, and Libertinus Serif embedded.
     *
     * @param title         the chapter's title, for the PDF's properties
     * @param book          the book the chapter belongs to, for the header and the PDF's author and language
     * @param baseDirectory the directory relative links and images resolve against, the book's chapters
     */
    public void manuscriptPdf(String markdown, String title, BookProperties book, Path baseDirectory, Path target) {

        String body = XhtmlRenderer.render(PARSER.parse(markdown));
        pdf.writeChapter(title, ManuscriptHeader.of(book), book, body, baseDirectory, target);
    }

    /**
     * Writes the chapter as an OpenDocument text file laid out like the manuscript PDF, in named styles an editor can
     * change. It names a common serif font without embedding one, so it opens alike in every word processor.
     *
     * @param title         the chapter's title, for the file's properties
     * @param book          the book the chapter belongs to, for the header and the file's author and language
     * @param baseDirectory the directory images resolve against, the book's chapters; the images are packed in
     */
    public void manuscriptOdt(String markdown, String title, BookProperties book, Path baseDirectory, Path target) {

        odt.writeChapter(PARSER.parse(markdown), title, ManuscriptHeader.of(book), book, baseDirectory, target);
    }
}
