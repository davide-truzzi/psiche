package it.infog.psiche.export.docx;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.common.Template;
import it.infog.psiche.export.XmlText;
import it.infog.psiche.export.document.WriteManuscript;
import it.infog.psiche.export.document.Pictures;
import it.infog.psiche.export.document.model.Picture;
import it.infog.psiche.export.docx.model.enums.Part;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ExportedChapter;


import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Writes a chapter or the whole book as a Word document (.docx), laid out like the ODT and the manuscript PDF.
 * <p>
 * The file is a zip ({@link DocxPackage}) of XML parts, each filled in from its template next to this class (see
 * {@link Part}): the text, written into a {@link DocxBody} by {@link WriteManuscript}, which only names styles; those
 * styles; the lists' numbering; the page header; the file's properties; and the images, found by {@link Pictures}.
 */
public final class DocxWriter {

    // Where the images are packed, numbered from 1.
    private static final String PICTURES = "word/media/image";

    private final Map<Part, Template> templates = new EnumMap<>(Part.class);

    public DocxWriter() {

        for (Part part : Part.values())
            templates.put(part, Template.load(DocxWriter.class, part.template()));
    }

    /**
     * Writes one chapter, alone.
     *
     * @param chapter       the chapter, its file title for the file's properties
     * @param header        the authors and the book's title, for the top of every page
     * @param book          the book the chapter belongs to, for the file's authors and language
     * @param baseDirectory the directory images are relative to, the book's chapters
     */
    public void writeChapter(ExportedChapter chapter, String header, BookProperties book, Path baseDirectory,
                             Path target) {

        DocxBody body = new DocxBody();
        Pictures pictures = new Pictures(baseDirectory, PICTURES);
        WriteManuscript.writeChapter(body, chapter, pictures);

        write(body, pictures, chapter.fileTitle(), header, book, target);
    }

    /**
     * Writes the whole book: the title page and the contents, without the header, then every chapter on a new page,
     * the first one numbered page 1. Each chapter starts with a bookmark, which its line in the contents links to.
     *
     * @param header the authors and the book's title, for the top of every page
     */
    public void writeBook(BookExport book, String header, Path target) {

        DocxBody body = new DocxBody();
        Pictures pictures = new Pictures(book.baseDirectory(), PICTURES);
        WriteManuscript.writeBook(body, book, pictures);

        write(body, pictures, book.properties().title(), header, book.properties(), target);
    }

    /** Packs the written {@code body}, its styles, properties and images into the file. */
    private void write(DocxBody body, Pictures pictures, String title, String header, BookProperties book,
                       Path target) {

        // Every part takes the values its template names; text in them is escaped.
        Map<String, String> values = Map.of(
                "body", body.xml(),
                "lists", body.lists(),
                "relationships", body.relationships(),
                "header", XmlText.escape(header),
                "title", XmlText.escape(title),
                "authors", XmlText.escape(String.join(", ", book.authors())),
                "language", XmlText.escape(book.language()));

        try (DocxPackage docx = new DocxPackage(target)) {
            for (Part part : Part.values())
                docx.add(part, templates.get(part).fill(values));
            for (Picture picture : pictures.all())
                docx.addPicture(picture);

        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + target, e);
        }
    }
}
