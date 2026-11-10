package it.infog.psiche.export;

import it.infog.psiche.book.model.BookProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static it.infog.psiche.export.ZipParts.entry;
import static it.infog.psiche.export.ZipParts.parse;
import static org.assertj.core.api.Assertions.assertThat;

class ChapterExporterTest {

    private static final BookProperties BOOK = new BookProperties("La strada del sale", "", List.of("Anna Bianchi"),
            "it-IT", "", "urn:uuid:1234");

    @TempDir
    Path tempDir;

    private final ChapterExporter exporter = new ChapterExporter();

    @Test
    void markdownIsWrittenAsItIs() throws IOException {
        Path target = tempDir.resolve("01-alba.md");
        String markdown = "# Alba\n\nIt began at **dawn**.\n\n<!-- a note -->\n\n![Map](../media/map.png)\n";

        exporter.markdown(markdown, target);

        assertThat(target).hasContent(markdown);
    }

    @Test
    void manuscriptPdfIsA4WithTheChapterAndAHeaderOnEveryPage() throws IOException {
        Path target = tempDir.resolve("01-alba.pdf");
        String chapter = "# Alba\n\nÈ così — «sì», disse lei… *piano*.\n\n"
                + "Una riga che riempie la pagina.\n\n".repeat(60);

        exporter.manuscriptPdf(chapter, "01-alba", BOOK, tempDir, target);

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            PDRectangle page = pdf.getPage(0).getMediaBox();
            assertThat(page.getWidth()).isCloseTo(PDRectangle.A4.getWidth(), org.assertj.core.data.Offset.offset(1f));
            assertThat(page.getHeight()).isCloseTo(PDRectangle.A4.getHeight(), org.assertj.core.data.Offset.offset(1f));
            assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo("01-alba");
            assertThat(pdf.getDocumentInformation().getAuthor()).isEqualTo("Anna Bianchi");

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(pdf);
            assertThat(text).contains("Alba", "È così — «sì», disse lei… piano.");

            stripper.setStartPage(2);
            stripper.setEndPage(2);
            assertThat(stripper.getText(pdf)).contains("Anna Bianchi — La strada del sale", "2");
        }
    }

    @Test
    void manuscriptPdfEmbedsLibertinusInEveryStyleUsed() throws IOException {
        Path target = tempDir.resolve("styles.pdf");

        exporter.manuscriptPdf("Regular, *italic*, **bold** and ***bold italic***.", "styles", BOOK, tempDir, target);

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            List<String> fonts = new ArrayList<>();
            var resources = pdf.getPage(0).getResources();
            for (var name : resources.getFontNames()) {
                PDFont font = resources.getFont(name);
                assertThat(font.isEmbedded()).as(font.getName()).isTrue();
                fonts.add(font.getName().substring(font.getName().indexOf('+') + 1));
            }
            assertThat(fonts).contains("LibertinusSerif-Regular", "LibertinusSerif-Italic", "LibertinusSerif-Bold",
                    "LibertinusSerif-BoldItalic");
        }
    }

    @Test
    void sceneBreaksAreAsterisksAndRawHtmlIsKeptAsText() throws IOException {
        Path target = tempDir.resolve("breaks.pdf");

        exporter.manuscriptPdf("Before.\n\n---\n\nAfter <b>this</b> & that.", "breaks", BOOK, tempDir, target);

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("* * *", "After <b>this</b> & that.");
        }
    }

    @Test
    void manuscriptPdfPrintsAFootnoteUnderItsPage() throws IOException {
        Path target = tempDir.resolve("notes.pdf");

        exporter.manuscriptPdf("Moon[^1] and stars.\n\n[^1]: A note.", "notes", BOOK, tempDir, target);

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Moon", "and stars.", "1. A note.");
        }
    }

    @Test
    void aMissingImageDoesNotStopTheExport() throws IOException {
        Path target = tempDir.resolve("image.pdf");

        exporter.manuscriptPdf("![Map](../media/missing.png)\n\nText after.", "image", BOOK, tempDir, target);

        assertThat(target).exists();
        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Text after.");
        }
    }

    @Test
    void odtStartsWithTheMimetypeUncompressed() throws IOException {
        Path target = tempDir.resolve("01-alba.odt");

        exporter.manuscriptOdt("# Alba\n\nText.", "01-alba", BOOK, tempDir, target);

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(target))) {
            ZipEntry first = zip.getNextEntry();
            assertThat(first.getName()).isEqualTo("mimetype");
            assertThat(first.getMethod()).isEqualTo(ZipEntry.STORED);
            assertThat(new String(zip.readAllBytes(), StandardCharsets.US_ASCII))
                    .isEqualTo("application/vnd.oasis.opendocument.text");
        }
    }

    @Test
    void odtKeepsStrikethroughSubscriptAndSuperscriptInTheirStyles() throws IOException {
        Path target = tempDir.resolve("scripts.odt");

        exporter.manuscriptOdt("~~gone~~ H~2~O x^2^", "scripts", BOOK, tempDir, target);

        assertThat(entry(target, "content.xml")).contains(
                "<text:span text:style-name=\"Strikethrough\">gone</text:span>",
                "H<text:span text:style-name=\"Subscript\">2</text:span>O",
                "x<text:span text:style-name=\"Superscript\">2</text:span>");
        assertThat(entry(target, "styles.xml")).contains("style:name=\"Strikethrough\"",
                "style:text-position=\"sub 58%\"", "style:text-position=\"super 58%\"");
    }

    @Test
    void odtPutsANoteAtItsFirstReferenceAndANoteReferenceAtTheNext() throws IOException {
        Path target = tempDir.resolve("notes.odt");

        exporter.manuscriptOdt("Moon[^m] and sun[^m].\n\n[^m]: A note.\n\n    Second.", "notes", BOOK, tempDir,
                target);

        assertThat(entry(target, "content.xml")).contains(
                "Moon<text:note text:id=\"ftn1\" text:note-class=\"footnote\"><text:note-citation>1"
                        + "</text:note-citation><text:note-body><text:p text:style-name=\"Footnote\">A note.</text:p>\n"
                        + "<text:p text:style-name=\"Footnote\">Second.</text:p>\n</text:note-body></text:note>"
                        + " and sun",
                "<text:note-ref text:note-class=\"footnote\" text:reference-format=\"text\" "
                        + "text:ref-name=\"ftn1\">1</text:note-ref>");
        assertThat(entry(target, "styles.xml")).contains("text:start-numbering-at=\"chapter\"",
                "style:name=\"Footnote\"");
    }

    @Test
    void odtKeepsTheChapterInNamedStyles() throws IOException {
        Path target = tempDir.resolve("styles.odt");
        String chapter = "# Alba\n\nÈ così — *piano*, **forte**.\n\nSecond.\n\n---\n\nAfter <b>this</b> & that.\n\n"
                + "> Quoted.\n\n- one\n- two\n\n3. three\n";

        exporter.manuscriptOdt(chapter, "styles", BOOK, tempDir, target);

        String content = entry(target, "content.xml");
        assertThat(content).contains(
                "<text:h text:style-name=\"Heading_20_1\" text:outline-level=\"1\">"
                        + "<text:bookmark text:name=\"chapter--alba\"/>Alba</text:h>",
                "<text:p text:style-name=\"First_20_Paragraph\">È così — "
                        + "<text:span text:style-name=\"Emphasis\">piano</text:span>, "
                        + "<text:span text:style-name=\"Strong_20_Emphasis\">forte</text:span>.</text:p>",
                "<text:p text:style-name=\"Text_20_body\">Second.</text:p>",
                "<text:p text:style-name=\"Scene_20_Break\">* * *</text:p>",
                "<text:p text:style-name=\"First_20_Paragraph\">After &lt;b&gt;this&lt;/b&gt; &amp; that.</text:p>",
                "<text:p text:style-name=\"Quotations\">Quoted.</text:p>",
                "<text:list text:style-name=\"Bullets\">",
                "<text:list-item><text:p text:style-name=\"List\">two</text:p>\n</text:list-item>",
                "<text:list-item text:start-value=\"3\"><text:p text:style-name=\"List\">three</text:p>");
    }

    @Test
    void odtLeavesOutHtmlCommentsAndTheirParagraphs() throws IOException {
        Path target = tempDir.resolve("comments.odt");

        exporter.manuscriptOdt("First.\n\n<!-- questa è una nota -->\n\nSecond<!-- inline --> <b>x</b>.", "comments",
                BOOK, tempDir, target);

        String content = entry(target, "content.xml");
        assertThat(content).doesNotContain("nota", "inline", "&lt;!--").contains(
                "<text:p text:style-name=\"First_20_Paragraph\">First.</text:p>\n"
                        + "<text:p text:style-name=\"Text_20_body\">Second &lt;b&gt;x&lt;/b&gt;.</text:p>");
    }

    @Test
    void manuscriptPdfLeavesOutHtmlComments() throws IOException {
        Path target = tempDir.resolve("comments.pdf");

        exporter.manuscriptPdf("First.\n\n<!-- questa è una nota -->\n\nSecond.", "comments", BOOK, tempDir, target);

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("First.", "Second.").doesNotContain("nota");
        }
    }

    @Test
    void odtPartsAreWellFormedAndNameTheBook() throws Exception {
        Path target = tempDir.resolve("book.odt");

        exporter.manuscriptOdt("Text & <more>.", "01-alba", BOOK, tempDir, target);

        for (String part : List.of("content.xml", "styles.xml", "meta.xml", "META-INF/manifest.xml"))
            parse(entry(target, part));
        String styles = entry(target, "styles.xml");
        assertThat(styles).contains("Anna Bianchi — La strada del sale<text:tab/><text:page-number",
                "fo:language=\"it\" fo:country=\"IT\"", "svg:font-family=\"'Liberation Serif'\"",
                "style:font-family-generic=\"roman\"", "fo:page-width=\"21cm\" fo:page-height=\"29.7cm\"");
        assertThat(entry(target, "meta.xml")).contains("<dc:title>01-alba</dc:title>",
                "<meta:initial-creator>Anna Bianchi</meta:initial-creator>", "<dc:language>it-IT</dc:language>");
    }

    @Test
    void odtPacksAnImageOfAChapterWhoseNameHasASpaceAsPsicheWritesIt() throws IOException {
        Path chapters = Files.createDirectories(tempDir.resolve("chapters"));
        Path media = Files.createDirectories(tempDir.resolve("media/Chapter three"));
        ImageIO.write(new BufferedImage(192, 96, BufferedImage.TYPE_INT_RGB), "png", media.resolve("map.png").toFile());
        Path target = tempDir.resolve("spaced.odt");

        exporter.manuscriptOdt("![Map](<../media/Chapter three/map.png>)\n", "spaced", BOOK, chapters, target);

        try (ZipFile zip = new ZipFile(target.toFile())) {
            assertThat(zip.getEntry("Pictures/image1.png")).isNotNull();
        }
    }

    @Test
    void odtPacksImagesAndLeavesOutMissingOnes() throws IOException {
        Path chapters = Files.createDirectories(tempDir.resolve("chapters"));
        Path media = Files.createDirectories(tempDir.resolve("media"));
        ImageIO.write(new BufferedImage(192, 96, BufferedImage.TYPE_INT_RGB), "png", media.resolve("map.png").toFile());
        Path target = tempDir.resolve("images.odt");

        exporter.manuscriptOdt("![Map](../media/map.png)\n\n![Gone](../media/missing.png)\n\nText after.", "images",
                BOOK, chapters, target);

        String content = entry(target, "content.xml");
        assertThat(content).containsOnlyOnce("<draw:frame").contains("svg:width=\"5.080cm\" svg:height=\"2.540cm\"",
                "xlink:href=\"Pictures/image1.png\"", "<svg:title>Map</svg:title>", "Text after.");
        assertThat(entry(target, "META-INF/manifest.xml"))
                .contains("manifest:full-path=\"Pictures/image1.png\" manifest:media-type=\"image/png\"");
        try (ZipFile zip = new ZipFile(target.toFile())) {
            assertThat(zip.getEntry("Pictures/image1.png").getSize()).isEqualTo(Files.size(media.resolve("map.png")));
        }
    }

    @Test
    void odtKeepsTheSpacesInCode() throws IOException {
        Path target = tempDir.resolve("code.odt");

        exporter.manuscriptOdt("```\n  x  y\n\tz\n```\n", "code", BOOK, tempDir, target);

        assertThat(entry(target, "content.xml")).contains("<text:p text:style-name=\"Preformatted_20_Text\">"
                + "<text:s text:c=\"2\"/>x <text:s/>y<text:line-break/><text:tab/>z</text:p>");
    }
}
