package it.infog.psiche.export.odt;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.common.Template;
import it.infog.psiche.export.XmlText;
import it.infog.psiche.export.document.WriteManuscript;
import it.infog.psiche.export.document.Pictures;
import it.infog.psiche.export.document.model.Picture;
import it.infog.psiche.export.model.BookExport;

import org.commonmark.node.Node;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a chapter or the whole book as an OpenDocument text file (.odt), laid out like the manuscript PDF.
 * <p>
 * The file is a zip ({@link OdtPackage}) of XML parts filled in from templates next to this class:
 * <ul>
 *     <li>{@code content.xml}: the text, written into an {@link OdtBody} by {@link WriteManuscript}, which only names
 *     styles;</li>
 *     <li>{@code styles.xml}: those styles, the A4 page and its header with the authors, title and page number;</li>
 *     <li>{@code meta.xml}: the title, authors and language a word processor shows in the file's properties;</li>
 *     <li>the images, found by {@link Pictures}.</li>
 * </ul>
 */
public final class OdtWriter {

    // Where the images are packed, numbered from 1.
    private static final String PICTURES = "Pictures/image";

    private final Template content = Template.load(OdtWriter.class, "content.xml");
    private final Template styles = Template.load(OdtWriter.class, "styles.xml");
    private final Template meta = Template.load(OdtWriter.class, "meta.xml");

    /**
     * Writes one chapter.
     *
     * @param document      the chapter's Markdown, parsed
     * @param title         the chapter's title, for the file's properties
     * @param header        the authors and the book's title, for the top of every page
     * @param book          the book the chapter belongs to, for the file's authors and language
     * @param baseDirectory the directory images are relative to, the book's chapters
     */
    public void writeChapter(Node document, String title, String header, BookProperties book, Path baseDirectory,
                             Path target) {

        OdtBody body = new OdtBody();
        Pictures pictures = new Pictures(baseDirectory, PICTURES);
        WriteManuscript.writeChapter(body, document, pictures);

        write(body, pictures, title, header, book, target);
    }

    /**
     * Writes the whole book: the title page and the contents, without the header, then every chapter on a new page,
     * the first one numbered page 1. Each chapter starts with a bookmark named after its id, which its line in the
     * contents links to and takes its page from.
     *
     * @param header the authors and the book's title, for the top of every page
     */
    public void writeBook(BookExport book, String header, Path target) {

        OdtBody body = new OdtBody();
        Pictures pictures = new Pictures(book.baseDirectory(), PICTURES);
        WriteManuscript.writeBook(body, book, pictures);

        write(body, pictures, book.properties().title(), header, book.properties(), target);
    }

    /** Packs the written {@code body}, its styles, properties and images into the file. */
    private void write(OdtBody body, Pictures pictures, String title, String header, BookProperties book,
                       Path target) {

        String text = content.fill(Map.of("automatic-styles", body.automaticStyles(), "body", body.xml()));

        try (OdtPackage odt = new OdtPackage(target)) {
            odt.addXml("content.xml", text);
            odt.addXml("styles.xml", styles(header, book.language()));
            odt.addXml("meta.xml", meta(title, book));
            for (Picture picture : pictures.all())
                odt.addPicture(picture);

        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + target, e);
        }
    }

    /**
     * The styles, with the page header and the language, which the word processor checks spelling and hyphenates in.
     *
     * @param language a BCP 47 tag such as {@code it-IT}; the styles take its language and country apart
     */
    private String styles(String header, String language) {

        Locale locale = Locale.forLanguageTag(language);

        return styles.fill(Map.of(
                "header", XmlText.escape(header),
                "language", locale.getLanguage().isEmpty() ? "none" : locale.getLanguage(),
                "country", locale.getCountry().isEmpty() ? "none" : locale.getCountry()));
    }

    private String meta(String title, BookProperties book) {

        return meta.fill(Map.of(
                "title", XmlText.escape(title),
                "authors", XmlText.escape(String.join(", ", book.authors())),
                "language", XmlText.escape(book.language())));
    }
}
