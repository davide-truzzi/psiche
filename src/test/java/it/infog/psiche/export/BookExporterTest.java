package it.infog.psiche.export;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ChapterText;
import it.infog.psiche.export.model.enums.EmptyChapters;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
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
import java.util.Optional;
import java.util.zip.ZipFile;

import static it.infog.psiche.export.ZipParts.entry;
import static it.infog.psiche.export.ZipParts.parse;
import static org.assertj.core.api.Assertions.assertThat;

class BookExporterTest {

    private static final BookProperties BOOK = new BookProperties("La strada del sale", "Romanzo",
            List.of("Anna Bianchi", "Marco Rossi"), "it-IT", "", "urn:uuid:1234");
    private static final String HEADER = "Anna Bianchi, Marco Rossi — La strada del sale";

    @TempDir
    Path tempDir;

    private final BookExporter exporter = new BookExporter();

    @Test
    void theTitlePageAndContentsWithoutHeaderThenEachChapterOnItsOwnPagesNumberedFromTheFirst() throws IOException {
        Path target = tempDir.resolve("book.pdf");
        BookExport book = BookAssembler.assemble(BOOK, Optional.empty(), Optional.empty(), tempDir, List.of(
                new ChapterText("01-alba", "# Alba\n\n" + "Una riga che riempie la pagina.\n\n".repeat(40)),
                new ChapterText("02-mezzogiorno", "# Mezzogiorno\n\nUna riga."),
                new ChapterText("03-sera", "Senza titolo, una riga.")), EmptyChapters.SKIP);

        exporter.manuscriptPdf(book, target);

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            // title page, contents, two pages of Alba, Mezzogiorno and Sera
            assertThat(pdf.getNumberOfPages()).isEqualTo(6);

            assertThat(page(pdf, 1)).contains("La strada del sale", "Romanzo", "Anna Bianchi, Marco Rossi")
                    .doesNotContain(HEADER);
            assertThat(page(pdf, 2)).startsWith("Indice").doesNotContain(HEADER);
            assertThat(page(pdf, 3)).startsWith(HEADER + " 1").contains("Alba");
            assertThat(page(pdf, 4)).startsWith(HEADER + " 2").doesNotContain("Mezzogiorno");
            assertThat(page(pdf, 5)).startsWith(HEADER + " 3").contains("Mezzogiorno", "Una riga.")
                    .doesNotContain("Senza titolo");
            assertThat(page(pdf, 6)).startsWith(HEADER + " 4").contains("Senza titolo, una riga.");

            assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo("La strada del sale");
            assertThat(pdf.getDocumentInformation().getAuthor()).isEqualTo("Anna Bianchi, Marco Rossi");
        }
    }

    @Test
    void placeholdersShowOnlyTheFileNameOnAPageOfTheirOwn() throws IOException {
        Path target = tempDir.resolve("placeholders.pdf");
        BookExport book = BookAssembler.assemble(BOOK, Optional.empty(), Optional.empty(), tempDir, List.of(
                new ChapterText("01-alba", "# Alba\n\nUna riga."),
                new ChapterText("02-vuoto", "  \n"),
                new ChapterText("03-sera", "# Sera\n\nUna riga.")), EmptyChapters.PLACEHOLDER);

        exporter.manuscriptPdf(book, target);

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(5);
            assertThat(page(pdf, 4).replace(HEADER + " 2", "").strip()).isEqualToNormalizingNewlines("02-vuoto\nΨ");
            assertThat(page(pdf, 5)).contains("Sera");
        }
    }

    @Test
    void theContentsListEachChapterWithItsPageLinkToItAndAreThePdfsBookmarks() throws IOException {
        Path target = tempDir.resolve("contents.pdf");
        BookExport book = BookAssembler.assemble(BOOK, Optional.empty(), Optional.empty(), tempDir, List.of(
                new ChapterText("01-alba", "# Alba\n\n" + "Una riga che riempie la pagina.\n\n".repeat(40)),
                new ChapterText("02-mezzogiorno", "# Mezzogiorno\n\nUna riga."),
                new ChapterText("03-sera", "Senza titolo, una riga.")), EmptyChapters.SKIP);

        exporter.manuscriptPdf(book, target);

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            // Alba runs to two pages, so Mezzogiorno starts on page 3; the page numbers follow dot leaders.
            assertThat(page(pdf, 2).lines().map(line -> line.replaceAll("\\s*\\.{3,}\\s*", " … ")))
                    .containsExactly("Indice", "Alba … 1", "Mezzogiorno … 3", "03-sera … 4");

            List<Integer> linked = new ArrayList<>();
            for (PDAnnotation annotation : pdf.getPage(1).getAnnotations())
                if (annotation instanceof PDAnnotationLink link)
                    linked.add(targetPage(pdf, link.getDestination() != null
                            ? link.getDestination()
                            : ((PDActionGoTo) link.getAction()).getDestination()));
            assertThat(linked).containsOnly(3, 5, 6).contains(3, 5, 6);

            List<String> bookmarks = new ArrayList<>();
            for (PDOutlineItem item : pdf.getDocumentCatalog().getDocumentOutline().children())
                bookmarks.add(item.getTitle() + " " + targetPage(pdf, item.getDestination()));
            assertThat(bookmarks).containsExactly("Indice 2", "Alba 3", "Mezzogiorno 5", "03-sera 6");
        }
    }

    @Test
    void theTitlePageShowsTheCover() throws IOException {
        Path cover = tempDir.resolve("cover.png");
        ImageIO.write(new BufferedImage(600, 900, BufferedImage.TYPE_INT_RGB), "png", cover.toFile());
        Path target = tempDir.resolve("cover.pdf");
        BookExport book = BookAssembler.assemble(BOOK, Optional.of(cover), Optional.empty(), tempDir,
                List.of(new ChapterText("01-alba", "# Alba")), EmptyChapters.SKIP);

        exporter.manuscriptPdf(book, target);

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            assertThat(images(pdf.getPage(0).getResources())).isEqualTo(1);
            assertThat(images(pdf.getPage(1).getResources())).isZero();
            assertThat(Files.size(target)).isPositive();
        }
    }

    @Test
    void odtStartsWithATitlePageThenEachChapterOnANewPageMarkedWithItsId() throws Exception {
        Path target = tempDir.resolve("book.odt");
        BookExport book = BookAssembler.assemble(BOOK, Optional.empty(), Optional.empty(), tempDir, List.of(
                new ChapterText("01-alba", "# Alba\n\nUna riga."),
                new ChapterText("02-vuoto", ""),
                new ChapterText("03-sera", "Senza titolo, una riga.")), EmptyChapters.PLACEHOLDER);

        exporter.manuscriptOdt(book, target);

        for (String part : List.of("content.xml", "styles.xml", "meta.xml", "META-INF/manifest.xml"))
            parse(entry(target, part));
        String content = entry(target, "content.xml");
        assertThat(content).containsSubsequence(
                // the title page, on the page style without header
                "<text:p text:style-name=\"Title_Page_Title\">La strada del sale</text:p>",
                "<text:p text:style-name=\"Subtitle\">Romanzo</text:p>",
                "<text:p text:style-name=\"Book_20_Authors\">Anna Bianchi, Marco Rossi</text:p>",
                // the contents, on a new page of the same page style, a line per chapter with its page
                "<text:p text:style-name=\"Contents_Page_Contents_20_Heading\"><text:bookmark text:name=\"toc\"/>"
                        + "Indice</text:p>",
                "<text:p text:style-name=\"Contents_20_1\"><text:a xlink:type=\"simple\" "
                        + "xlink:href=\"#chapter-2\" text:style-name=\"Index_20_Link\" "
                        + "text:visited-style-name=\"Index_20_Link\">02-vuoto</text:a>"
                        + "<text:tab/><text:bookmark-ref text:reference-format=\"page\" text:ref-name=\"chapter-2\">?"
                        + "</text:bookmark-ref></text:p>",
                // the first chapter, back on the manuscript page numbered from 1, then a page break before each other
                "<text:h text:style-name=\"First_Chapter_Heading_20_1\" text:outline-level=\"1\">"
                        + "<text:bookmark text:name=\"chapter-1\"/>Alba</text:h>",
                "<text:h text:style-name=\"Chapter_Heading_20_1\" text:outline-level=\"1\">"
                        + "<text:bookmark text:name=\"chapter-2\"/>02-vuoto</text:h>",
                "<text:p text:style-name=\"Chapter_First_20_Paragraph\"><text:bookmark text:name=\"chapter-3\"/>"
                        + "Senza titolo, una riga.</text:p>");
        assertThat(content).contains(
                "style:name=\"Title_Page_Title\" style:family=\"paragraph\" style:parent-style-name=\"Title\" "
                        + "style:master-page-name=\"Front_20_Matter\"",
                "style:parent-style-name=\"Heading_20_1\" style:master-page-name=\"Standard\">"
                        + "<style:paragraph-properties style:page-number=\"1\"/>",
                "style:parent-style-name=\"First_20_Paragraph\">"
                        + "<style:paragraph-properties fo:break-before=\"page\"/>");
        assertThat(entry(target, "styles.xml")).contains("<style:master-page style:name=\"Front_20_Matter\"");
        assertThat(entry(target, "meta.xml")).contains("<dc:title>La strada del sale</dc:title>");
    }

    @Test
    void odtPacksTheCoverOnTheTitlePage() throws IOException {
        Path cover = tempDir.resolve("cover.png");
        ImageIO.write(new BufferedImage(600, 900, BufferedImage.TYPE_INT_RGB), "png", cover.toFile());
        Path target = tempDir.resolve("cover.odt");
        BookExport book = BookAssembler.assemble(BOOK, Optional.of(cover), Optional.empty(), tempDir,
                List.of(new ChapterText("01-alba", "# Alba")), EmptyChapters.SKIP);

        exporter.manuscriptOdt(book, target);

        // 600 by 900 pixels shrunk to the cover's 13cm height
        assertThat(entry(target, "content.xml")).contains("<text:p text:style-name=\"Title_Page_Cover\"><draw:frame",
                "svg:width=\"8.667cm\" svg:height=\"13.000cm\"", "xlink:href=\"Pictures/image1.png\"");
        assertThat(entry(target, "META-INF/manifest.xml")).contains("manifest:full-path=\"Pictures/image1.png\"");
    }


    /** The number, from 1, of the page {@code destination} leads to. */
    private static int targetPage(PDDocument pdf, PDDestination destination) throws IOException {
        return ((PDPageDestination) destination).retrievePageNumber() + 1;
    }

    private static String page(PDDocument pdf, int number) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(number);
        stripper.setEndPage(number);
        return stripper.getText(pdf).strip();
    }

    private static int images(PDResources resources) throws IOException {
        int count = 0;
        for (COSName name : resources.getXObjectNames())
            if (resources.getXObject(name) instanceof PDImageXObject)
                count++;
        return count;
    }
}
