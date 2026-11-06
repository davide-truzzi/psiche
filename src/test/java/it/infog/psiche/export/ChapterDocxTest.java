package it.infog.psiche.export;

import it.infog.psiche.book.model.BookProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static it.infog.psiche.export.ZipParts.entry;
import static it.infog.psiche.export.ZipParts.parse;
import static org.assertj.core.api.Assertions.assertThat;

class ChapterDocxTest {

    private static final BookProperties BOOK = new BookProperties("La strada del sale", "", List.of("Anna Bianchi"),
            "it-IT", "", "urn:uuid:1234");
    private static final String DOCUMENT = "word/document.xml";

    @TempDir
    Path tempDir;

    private final ChapterExporter exporter = new ChapterExporter();

    @Test
    void partsAreWellFormedListedAndNameTheBook() throws Exception {
        Path target = tempDir.resolve("01-alba.docx");

        exporter.manuscriptDocx("Text & more.", "01-alba", BOOK, tempDir, target);

        List<String> parts = List.of("[Content_Types].xml", "_rels/.rels", DOCUMENT, "word/_rels/document.xml.rels",
                "word/styles.xml", "word/numbering.xml", "word/settings.xml", "word/header1.xml", "docProps/core.xml",
                "docProps/app.xml");
        for (String part : parts)
            parse(entry(target, part));
        try (ZipFile zip = new ZipFile(target.toFile())) {
            assertThat(zip.getEntry("mimetype")).isNull();
        }
        assertThat(entry(target, "[Content_Types].xml")).contains("<Override PartName=\"/word/document.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>");
        assertThat(entry(target, "word/header1.xml"))
                .contains("Anna Bianchi — La strada del sale</w:t></w:r><w:r><w:tab/></w:r>"
                        + "<w:fldSimple w:instr=\" PAGE \"")
                .doesNotContain("<!--");
        assertThat(entry(target, "word/styles.xml"))
                .contains("<w:lang w:val=\"it-IT\"/>", "w:ascii=\"Liberation Serif\"");
        assertThat(entry(target, "docProps/core.xml")).contains("<dc:title>01-alba</dc:title>",
                "<dc:creator>Anna Bianchi</dc:creator>", "<dc:language>it-IT</dc:language>");
        assertThat(entry(target, DOCUMENT)).contains("Text &amp; more.",
                "<w:headerReference w:type=\"default\" r:id=\"header\"/><w:pgSz w:w=\"11906\" w:h=\"16838\"/>");
    }

    @Test
    void keepsTheChapterInNamedStyles() throws IOException {
        Path target = tempDir.resolve("styles.docx");
        String chapter = "# Alba\n\nÈ così — *piano*, **forte**.\n\nSecond.\n\n---\n\nAfter <b>this</b>.\n\n"
                + "> Quoted.\n";

        exporter.manuscriptDocx(chapter, "styles", BOOK, tempDir, target);

        assertThat(entry(target, DOCUMENT)).contains(
                "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t xml:space=\"preserve\">Alba</w:t></w:r>"
                        + "</w:p>",
                "<w:pStyle w:val=\"FirstParagraph\"/></w:pPr><w:r><w:t xml:space=\"preserve\">È così — </w:t></w:r>"
                        + "<w:r><w:rPr><w:rStyle w:val=\"Emphasis\"/></w:rPr><w:t xml:space=\"preserve\">piano"
                        + "</w:t></w:r>",
                "<w:rPr><w:rStyle w:val=\"Strong\"/></w:rPr><w:t xml:space=\"preserve\">forte</w:t>",
                "<w:pStyle w:val=\"BodyText\"/></w:pPr><w:r><w:t xml:space=\"preserve\">Second.</w:t>",
                "<w:pStyle w:val=\"SceneBreak\"/></w:pPr><w:r><w:t xml:space=\"preserve\">* * *</w:t>",
                "<w:t xml:space=\"preserve\">&lt;b&gt;</w:t>",
                "<w:pStyle w:val=\"Quotations\"/></w:pPr><w:r><w:t xml:space=\"preserve\">Quoted.</w:t>");
    }

    @Test
    void boldInsideItalicIsBoth() throws IOException {
        Path target = tempDir.resolve("nested.docx");

        exporter.manuscriptDocx("*a **b** c*", "nested", BOOK, tempDir, target);

        assertThat(entry(target, DOCUMENT)).contains(
                "<w:rPr><w:rStyle w:val=\"Strong\"/><w:i/></w:rPr><w:t xml:space=\"preserve\">b</w:t>");
    }

    @Test
    void eachNumberedListStartsAtItsOwnNumber() throws IOException {
        Path target = tempDir.resolve("lists.docx");

        exporter.manuscriptDocx("3. three\n4. four\n\nText.\n\n1. one\n   - nested\n\n- dot\n", "lists", BOOK, tempDir,
                target);

        assertThat(entry(target, "word/numbering.xml")).contains(
                "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"1\"/><w:lvlOverride w:ilvl=\"0\"><w:startOverride "
                        + "w:val=\"3\"/></w:lvlOverride></w:num>",
                "<w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/><w:lvlOverride w:ilvl=\"0\"><w:startOverride "
                        + "w:val=\"1\"/></w:lvlOverride></w:num>",
                "<w:num w:numId=\"3\"><w:abstractNumId w:val=\"0\"/></w:num>",
                "<w:num w:numId=\"4\"><w:abstractNumId w:val=\"0\"/></w:num>");
        assertThat(entry(target, DOCUMENT)).contains(
                "<w:pStyle w:val=\"List\"/><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>"
                        + "</w:pPr><w:r><w:t xml:space=\"preserve\">four",
                "<w:numPr><w:ilvl w:val=\"1\"/><w:numId w:val=\"3\"/></w:numPr></w:pPr><w:r><w:t "
                        + "xml:space=\"preserve\">nested",
                "<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"4\"/></w:numPr></w:pPr><w:r><w:t "
                        + "xml:space=\"preserve\">dot");
    }

    @Test
    void webLinksStayLinksAndLinksToTheBookKeepTheirText() throws IOException {
        Path target = tempDir.resolve("links.docx");

        exporter.manuscriptDocx("See [the site](https://example.com/?a=1&b=2) and [the map](../media/map.md).",
                "links", BOOK, tempDir, target);

        assertThat(entry(target, DOCUMENT)).contains("<w:hyperlink r:id=\"rId1\" w:history=\"1\"><w:r><w:rPr>"
                + "<w:rStyle w:val=\"Hyperlink\"/></w:rPr><w:t xml:space=\"preserve\">the site</w:t></w:r>"
                + "</w:hyperlink>",
                "<w:t xml:space=\"preserve\">the map</w:t>").containsOnlyOnce("<w:hyperlink");
        assertThat(entry(target, "word/_rels/document.xml.rels")).contains("<Relationship Id=\"rId1\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" "
                + "Target=\"https://example.com/?a=1&amp;b=2\" TargetMode=\"External\"/>");
    }

    @Test
    void packsImagesAndLeavesOutMissingOnes() throws IOException {
        Path chapters = Files.createDirectories(tempDir.resolve("chapters"));
        Path media = Files.createDirectories(tempDir.resolve("media"));
        ImageIO.write(new BufferedImage(192, 96, BufferedImage.TYPE_INT_RGB), "png", media.resolve("map.png").toFile());
        Path target = tempDir.resolve("images.docx");

        exporter.manuscriptDocx("![Map](../media/map.png)\n\n![Gone](../media/missing.png)\n\n"
                + "![Again](../media/map.png)", "images", BOOK, chapters, target);

        // 192 by 96 pixels at 96 dpi: 5.08 by 2.54cm, in 360000ths of a centimetre
        assertThat(entry(target, DOCUMENT)).contains("<w:pStyle w:val=\"Figure\"/>",
                "<wp:extent cx=\"1828800\" cy=\"914400\"/>\n<wp:docPr id=\"1\" name=\"Picture 1\" descr=\"Map\"/>",
                "<wp:docPr id=\"2\" name=\"Picture 2\" descr=\"Again\"/>", "<a:blip r:embed=\"rId1\"/>",
                "<w:pStyle w:val=\"Caption\"/>")
                .doesNotContain("Gone");
        assertThat(entry(target, "word/styles.xml")).contains("w:styleId=\"Caption\"");
        assertThat(entry(target, "word/_rels/document.xml.rels")).containsOnlyOnce("Target=\"media/image1.png\"");
        assertThat(entry(target, "[Content_Types].xml"))
                .contains("<Default Extension=\"png\" ContentType=\"image/png\"/>");
        try (ZipFile zip = new ZipFile(target.toFile())) {
            assertThat(zip.getEntry("word/media/image1.png").getSize()).isEqualTo(Files.size(media.resolve("map.png")));
        }
    }

    @Test
    void keepsTheSpacesInCodeAndLeavesOutHtmlCommentsWithTheirSpace() throws IOException {
        Path target = tempDir.resolve("code.docx");

        exporter.manuscriptDocx("```\n  x  y\n\tz\n```\n\n<!-- questa è una nota -->\n\nSecond <!-- inline --> one.",
                "code", BOOK, tempDir, target);

        assertThat(entry(target, DOCUMENT)).contains("<w:pStyle w:val=\"PreformattedText\"/></w:pPr><w:r><w:t "
                + "xml:space=\"preserve\">  x  y</w:t></w:r><w:r><w:br/></w:r><w:r><w:t xml:space=\"preserve\"></w:t>"
                + "<w:tab/><w:t xml:space=\"preserve\">z</w:t></w:r>",
                // one space where the comment was, as a word processor shows the ODT's
                "<w:t xml:space=\"preserve\">Second </w:t></w:r><w:r><w:t xml:space=\"preserve\">one.</w:t>")
                .doesNotContain("nota", "inline", "&lt;!--");
    }
}
