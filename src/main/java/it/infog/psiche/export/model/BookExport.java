package it.infog.psiche.export.model;

import it.infog.psiche.book.model.BookProperties;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The whole book, ready to export to any format.
 *
 * @param properties    the title, authors and language, for the file's properties and the page header
 * @param titlePage     what the book's first page shows
 * @param contents      the table of contents, a line per chapter
 * @param stylesheet    the book's own stylesheet for ebooks, {@code media/style.css}, if it has one
 * @param baseDirectory the directory the chapters' image links are relative to, the book's chapters
 * @param chapters      the chapters, in the order of the book
 */
public record BookExport(BookProperties properties,
                         TitlePage titlePage,
                         Contents contents,
                         Optional<Path> stylesheet,
                         Path baseDirectory,
                         List<ExportedChapter> chapters) {

    public BookExport {

        chapters = List.copyOf(chapters);
    }
}
