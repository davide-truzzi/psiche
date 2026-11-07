package it.infog.psiche.export;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ChapterText;
import it.infog.psiche.export.model.enums.EmptyChapters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static it.infog.psiche.export.ZipParts.entry;
import static it.infog.psiche.export.ZipParts.parse;
import static org.assertj.core.api.Assertions.assertThat;

class BookDocxTest {

    private static final BookProperties BOOK = new BookProperties("La strada del sale", "Romanzo",
            List.of("Anna Bianchi", "Marco Rossi"), "it-IT", "", "urn:uuid:1234");
    private static final String DOCUMENT = "word/document.xml";
    private static final String TEXT = "<w:t xml:space=\"preserve\">";

    @TempDir
    Path tempDir;

    private final BookExporter exporter = new BookExporter();

    @Test
    void frontMatterWithoutHeaderThenEachChapterOnANewPageNumberedFromTheFirst() throws Exception {
        Path target = tempDir.resolve("book.docx");
        BookExport book = BookAssembler.assemble(BOOK, Optional.empty(), Optional.empty(), tempDir, List.of(
                new ChapterText("01-alba", "# Alba\n\nUna riga."),
                new ChapterText("02-vuoto", ""),
                new ChapterText("03-sera", "Senza titolo, una riga.")), EmptyChapters.PLACEHOLDER);

        exporter.manuscriptDocx(book, target);

        String document = entry(target, DOCUMENT);
        parse(document);
        assertThat(document).containsSubsequence(
                // the title page
                "<w:pStyle w:val=\"Title\"/></w:pPr><w:r>" + TEXT + "La strada del sale",
                "<w:pStyle w:val=\"Subtitle\"/></w:pPr><w:r>" + TEXT + "Romanzo",
                "<w:pStyle w:val=\"BookAuthors\"/></w:pPr><w:r>" + TEXT + "Anna Bianchi, Marco Rossi",
                // the contents on a new page, a line per chapter linked to its bookmark, without its page
                "<w:pStyle w:val=\"TOCHeading\"/><w:pageBreakBefore/></w:pPr>"
                        + "<w:bookmarkStart w:id=\"1\" w:name=\"_Bookmark1\"/><w:bookmarkEnd w:id=\"1\"/><w:r>"
                        + TEXT + "Indice",
                "<w:pStyle w:val=\"TOC1\"/></w:pPr><w:hyperlink w:anchor=\"_Bookmark3\" w:history=\"1\"><w:r><w:rPr>"
                        + "<w:rStyle w:val=\"IndexLink\"/></w:rPr>" + TEXT + "02-vuoto</w:t></w:r></w:hyperlink></w:p>",
                // the front matter's section ends with the last line of the contents, without the header
                "<w:pStyle w:val=\"TOC1\"/><w:sectPr><w:pgSz ",
                // the first chapter starts the next section, the others a new page, each at its bookmark; a heading
                // carries its own after it, which links to the heading lead to
                "<w:pStyle w:val=\"Heading1\"/></w:pPr><w:bookmarkStart w:id=\"2\" w:name=\"_Bookmark2\"/>"
                        + "<w:bookmarkEnd w:id=\"2\"/><w:bookmarkStart w:id=\"3\" w:name=\"_Bookmark5\"/>"
                        + "<w:bookmarkEnd w:id=\"3\"/><w:r>" + TEXT + "Alba",
                "<w:pStyle w:val=\"Heading1\"/><w:pageBreakBefore/></w:pPr>"
                        + "<w:bookmarkStart w:id=\"4\" w:name=\"_Bookmark3\"/>",
                "<w:pStyle w:val=\"FirstParagraph\"/><w:pageBreakBefore/></w:pPr>"
                        + "<w:bookmarkStart w:id=\"6\" w:name=\"_Bookmark4\"/><w:bookmarkEnd w:id=\"6\"/><w:r>"
                        + TEXT + "Senza titolo, una riga.",
                // the chapters' section, with the header, numbered from 1
                "<w:sectPr><w:headerReference w:type=\"default\" r:id=\"header\"/>",
                "<w:pgNumType w:start=\"1\"/></w:sectPr></w:body>");
        assertThat(document).doesNotContain("PAGEREF").containsOnlyOnce("<w:headerReference");
        assertThat(entry(target, "docProps/core.xml")).contains("<dc:title>La strada del sale</dc:title>");
    }

    @Test
    void packsTheCoverOnTheTitlePage() throws IOException {
        Path cover = tempDir.resolve("cover.png");
        ImageIO.write(new BufferedImage(600, 900, BufferedImage.TYPE_INT_RGB), "png", cover.toFile());
        Path target = tempDir.resolve("cover.docx");
        BookExport book = BookAssembler.assemble(BOOK, Optional.of(cover), Optional.empty(), tempDir,
                List.of(new ChapterText("01-alba", "# Alba")), EmptyChapters.SKIP);

        exporter.manuscriptDocx(book, target);

        // 600 by 900 pixels shrunk to the cover's 13cm height, in 360000ths of a centimetre
        assertThat(entry(target, DOCUMENT)).contains("<w:pStyle w:val=\"Cover\"/></w:pPr><w:r><w:drawing>",
                "cy=\"4680000\"", "descr=\"La strada del sale\"");
        assertThat(entry(target, "word/_rels/document.xml.rels")).contains("Target=\"media/image1.png\"");
    }

    @Test
    void linksToAChapterOrHeadingLeadToItsBookmarkInTheLinksStyle() throws Exception {
        Path target = tempDir.resolve("links.docx");
        BookExport book = BookAssembler.assemble(BOOK, Optional.empty(), Optional.empty(), tempDir, List.of(
                new ChapterText("01-alba", "# Alba\n\n[la tempesta](02-due.md#la-tempesta), [niente](03-niente.md)"),
                new ChapterText("02-due", "# Due\n\n## La tempesta\n")), EmptyChapters.SKIP);

        exporter.manuscriptDocx(book, target);

        String document = entry(target, DOCUMENT);
        parse(document);
        // The contents name the chapters' bookmarks first, 2 and 3; Alba's heading takes 4, the link 5, which Due's
        // heading carries, as the document's sixth bookmark.
        assertThat(document).contains(
                "<w:hyperlink w:anchor=\"_Bookmark5\" w:history=\"1\"><w:r><w:rPr><w:rStyle w:val=\"Hyperlink\"/>"
                        + "</w:rPr>" + TEXT + "la tempesta</w:t></w:r></w:hyperlink>",
                TEXT + "niente</w:t>",
                "<w:bookmarkStart w:id=\"6\" w:name=\"_Bookmark5\"/><w:bookmarkEnd w:id=\"6\"/><w:r>" + TEXT
                        + "La tempesta");
        assertThat(document).doesNotContain("03-niente");
    }
}
