package it.infog.psiche.export;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ChapterText;
import it.infog.psiche.export.model.Contents;
import it.infog.psiche.export.model.ExportedChapter;
import it.infog.psiche.export.model.TitlePage;
import it.infog.psiche.export.model.enums.EmptyChapters;
import it.infog.psiche.markdown.MarkdownSyntax;
import it.infog.psiche.markdown.PlainText;

import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Puts the book's chapters together for export, in the order it is given them. Each chapter is parsed on its own, so
 * a code block a chapter leaves open, or a link definition, never runs into the next one.
 */
public final class BookAssembler {

    private static final Parser PARSER = MarkdownSyntax.parser();
    // The id of a chapter exported alone, which the headings' ids start with.
    private static final String ALONE_ID = "chapter";

    private BookAssembler() {
    }

    /**
     * @param cover         the cover image, for the title page
     * @param stylesheet    the book's own stylesheet for ebooks, if it has one
     * @param baseDirectory the directory the chapters' image links are relative to, the book's chapters
     * @param chapters      the chapters, in the order of the book's chapter list
     * @param empty         what to do with chapters that have no text
     */
    public static BookExport assemble(BookProperties properties, Optional<Path> cover, Optional<Path> stylesheet,
                                      Path baseDirectory, List<ChapterText> chapters, EmptyChapters empty) {

        List<ExportedChapter> exported = new ArrayList<>();
        for (ChapterText chapter : chapters) {
            if (chapter.isEmpty() && empty == EmptyChapters.SKIP)
                continue;

            // Numbered after skipping, so ids stay one per chapter in the book: chapter-1, chapter-2…
            String id = "chapter-" + (exported.size() + 1);
            Node document = chapter.isEmpty() ? placeholder(chapter.fileTitle()) : PARSER.parse(chapter.markdown());
            exported.add(new ExportedChapter(id, chapter.fileTitle(), title(document).orElse(chapter.fileTitle()),
                    document));
        }

        return new BookExport(properties, TitlePage.of(properties, cover), Contents.of(properties.language(), exported),
                stylesheet, baseDirectory, exported);
    }

    /**
     * A chapter exported alone, out of the book.
     *
     * @param fileTitle the chapter's file name without its extension
     * @param markdown  the chapter's text
     */
    public static ExportedChapter alone(String fileTitle, String markdown) {

        Node document = PARSER.parse(markdown);
        return new ExportedChapter(ALONE_ID, fileTitle, title(document).orElse(fileTitle), document);
    }

    /** An empty chapter's stand-in: its file name as its heading, and nothing else. */
    private static Node placeholder(String fileTitle) {

        Heading heading = new Heading();
        heading.setLevel(1);
        heading.appendChild(new Text(fileTitle));

        Document document = new Document();
        document.appendChild(heading);

        return document;
    }

    /** The plain text of the chapter's first heading, without its Markdown; empty if it has none. */
    private static Optional<String> title(Node document) {

        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                String text = PlainText.of(heading).strip();
                if (!text.isEmpty())
                    return Optional.of(text);
            }
        }

        return Optional.empty();
    }
}
