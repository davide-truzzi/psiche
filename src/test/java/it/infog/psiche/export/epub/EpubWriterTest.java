package it.infog.psiche.export.epub;

import com.adobe.epubcheck.tool.EpubChecker;
import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.BookAssembler;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ChapterText;
import it.infog.psiche.export.model.enums.EmptyChapters;
import it.infog.psiche.export.model.enums.EpubVariant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class EpubWriterTest {

    private static final String ID = "urn:uuid:6ea76572-e37d-453a-a0c5-1f1fe4fce224";
    private static final BookProperties BOOK = new BookProperties("La strada del sale", "Romanzo",
            List.of("Anna Bianchi", "Marco Rossi"), "it-IT", "© 2026 Anna Bianchi", ID);
    private static final Instant NOW = Instant.parse("2026-10-31T10:15:30.123Z");

    @TempDir
    Path tempDir;

    private final EpubWriter writer = new EpubWriter(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void anEpubForTheStoresPassesEpubCheckWithCoverImagesAndPlaceholders() throws IOException {
        Path target = tempDir.resolve("book.epub");

        writer.write(book(Optional.of(image("cover.jpg", 1600, 2400)), EmptyChapters.PLACEHOLDER), EpubVariant.STORES,
                target);

        assertPassesEpubCheck(target);
    }

    @Test
    void anEpubForKindlePassesEpubCheckWithoutAnyCover() throws IOException {
        Path target = tempDir.resolve("kindle.epub");

        writer.write(book(Optional.of(image("cover.jpg", 1600, 2400)), EmptyChapters.SKIP), EpubVariant.KINDLE,
                target);

        assertPassesEpubCheck(target);
        String opf = entry(target, "EPUB/package.opf");
        assertThat(opf).doesNotContain("cover");
        assertThat(entries(target)).noneMatch(name -> name.contains("cover"));
    }

    @Test
    void readsCoverTitlePageContentsChaptersThenTheCopyrightAtTheBack() throws IOException {
        Path target = tempDir.resolve("order.epub");

        writer.write(book(Optional.of(image("cover.png", 1600, 2400)), EmptyChapters.PLACEHOLDER), EpubVariant.STORES,
                target);

        assertThat(entry(target, "EPUB/package.opf")).containsSubsequence(
                "<itemref idref=\"cover\"/>",
                "<itemref idref=\"title-page\"/>",
                "<itemref idref=\"nav\"/>",
                "<itemref idref=\"chapter-1\"/>",
                "<itemref idref=\"chapter-2\"/>",
                "<itemref idref=\"chapter-3\"/>",
                "<itemref idref=\"copyright\"/>");
        assertThat(entry(target, "EPUB/xhtml/copyright.xhtml")).contains("<p>© 2026 Anna Bianchi</p>");
        assertThat(entry(target, "EPUB/xhtml/title-page.xhtml")).contains(
                "<p class=\"book-title\">La strada del sale</p>", "<p class=\"book-subtitle\">Romanzo</p>",
                "<p class=\"book-authors\">Anna Bianchi, Marco Rossi</p>").doesNotContain("<img");
        assertThat(entry(target, "EPUB/xhtml/cover.xhtml")).contains("viewBox=\"0 0 1600 2400\"",
                "xlink:href=\"../images/cover.png\"", "aria-label=\"La strada del sale\"");
    }

    @Test
    void aBookWithoutRightsHasNoCopyrightPage() throws IOException {
        Path target = tempDir.resolve("no-rights.epub");
        BookProperties properties = new BookProperties("Titolo", "", List.of("Anna Bianchi"), "it-IT", "", "id");
        BookExport book = BookAssembler.assemble(properties, Optional.empty(), Optional.empty(), tempDir,
                List.of(new ChapterText("01-alba", "# Alba\n\nText.")), EmptyChapters.SKIP);

        writer.write(book, EpubVariant.STORES, target);

        assertPassesEpubCheck(target);
        assertThat(entry(target, "EPUB/package.opf")).doesNotContain("copyright", "dc:rights", "subtitle");
    }

    @Test
    void describesTheBookForStoresAndScreenReaders() throws IOException {
        Path target = tempDir.resolve("metadata.epub");

        writer.write(book(Optional.of(image("cover.jpg", 1600, 2400)), EmptyChapters.SKIP), EpubVariant.STORES,
                target);

        assertThat(entry(target, "EPUB/package.opf")).contains(
                "<dc:identifier id=\"book-id\">" + ID + "</dc:identifier>",
                "<dc:title id=\"title\">La strada del sale</dc:title>",
                "<meta refines=\"#subtitle\" property=\"title-type\">subtitle</meta>",
                "<dc:creator id=\"author-2\">Marco Rossi</dc:creator>",
                "<meta refines=\"#author-2\" property=\"role\" scheme=\"marc:relators\">aut</meta>",
                "<dc:language>it-IT</dc:language>",
                "<meta property=\"dcterms:modified\">2026-10-31T10:15:30Z</meta>",
                "<meta name=\"cover\" content=\"cover-image\"/>",
                "properties=\"cover-image\"",
                "<meta property=\"schema:accessMode\">visual</meta>",
                "<meta property=\"schema:accessibilityFeature\">tableOfContents</meta>",
                "<meta property=\"schema:accessibilityHazard\">none</meta>");
        // One of the chapters' images has no description, so text alone is not enough.
        assertThat(entry(target, "EPUB/package.opf"))
                .doesNotContain("<meta property=\"schema:accessModeSufficient\">textual</meta>", "alternativeText");
        assertThat(entry(target, "EPUB/xhtml/nav.xhtml")).contains("<h1 id=\"toc-heading\">Indice</h1>",
                "<li><a href=\"chapter-1.xhtml\">Alba</a></li>", "epub:type=\"bodymatter\" href=\"chapter-1.xhtml\"");
        assertThat(entry(target, "EPUB/toc.ncx")).contains("<meta name=\"dtb:uid\" content=\"" + ID + "\"/>",
                "<text>Alba</text>", "<content src=\"xhtml/chapter-1.xhtml\"/>");
    }

    @Test
    void packsEachImageOnceShrinkingTheLargeOnesAndLeavingOutTheMissing() throws IOException {
        Path target = tempDir.resolve("images.epub");
        image("big.png", 2400, 2400);

        writer.write(book(Optional.empty(), EmptyChapters.SKIP), EpubVariant.STORES, target);

        // map.png once though two chapters show it, big.png shrunk under the limit, persa.png missing
        assertThat(entries(target)).filteredOn(name -> name.startsWith("EPUB/images/")).containsExactly(
                "EPUB/images/image-1.png", "EPUB/images/image-2.png");
        BufferedImage map = ImageIO.read(new ByteArrayInputStream(bytes(target, "EPUB/images/image-1.png")));
        BufferedImage big = ImageIO.read(new ByteArrayInputStream(bytes(target, "EPUB/images/image-2.png")));
        assertThat(map.getWidth()).isEqualTo(600);
        assertThat((long) big.getWidth() * big.getHeight()).isLessThanOrEqualTo(EpubImages.MAX_PIXELS)
                .isGreaterThan(EpubImages.MAX_PIXELS - 5_000);
        assertThat(entry(target, "EPUB/xhtml/chapter-1.xhtml")).contains("<hr />",
                "<img src=\"../images/image-1.png\" alt=\"La mappa\" />",
                "<img src=\"../images/image-2.png\" alt=\"La mappa\" />");
        assertThat(entry(target, "EPUB/xhtml/chapter-2.xhtml")).contains(
                "<img src=\"../images/image-1.png\" alt=\"\" />").doesNotContain("persa");
    }

    @Test
    void usesTheBooksOwnStylesheetOrWritersDefault() throws IOException {
        Path style = Files.writeString(tempDir.resolve("style.css"), "p { color: black; }");
        BookExport own = BookAssembler.assemble(BOOK, Optional.empty(), Optional.of(style), tempDir,
                List.of(new ChapterText("01-alba", "# Alba")), EmptyChapters.SKIP);
        BookExport none = BookAssembler.assemble(BOOK, Optional.empty(), Optional.empty(), tempDir,
                List.of(new ChapterText("01-alba", "# Alba")), EmptyChapters.SKIP);

        writer.write(own, EpubVariant.STORES, tempDir.resolve("own.epub"));
        writer.write(none, EpubVariant.STORES, tempDir.resolve("none.epub"));

        assertThat(entry(tempDir.resolve("own.epub"), "EPUB/css/style.css")).isEqualTo("p { color: black; }");
        assertThat(entry(tempDir.resolve("none.epub"), "EPUB/css/style.css")).contains("hr::after");
    }

    @Test
    void checksForWhatStoresWouldObjectTo() throws IOException {
        BookExport noCover = book(Optional.empty(), EmptyChapters.SKIP);
        BookExport smallCover = book(Optional.of(image("small.jpg", 600, 900)), EmptyChapters.SKIP);

        assertThat(writer.check(noCover, EpubVariant.STORES)).hasSize(3)
                .anySatisfy(problem -> assertThat(problem).contains("no cover"))
                .anySatisfy(problem -> assertThat(problem).contains("../media/persa.png (not found)"))
                .anySatisfy(problem -> assertThat(problem).startsWith("1 image has no description"));
        assertThat(writer.check(smallCover, EpubVariant.STORES))
                .anySatisfy(problem -> assertThat(problem).startsWith("The cover is 600 × 900 pixels."));
        // Kindle adds its own cover, so the book's does not matter.
        assertThat(writer.check(noCover, EpubVariant.KINDLE)).noneMatch(problem -> problem.contains("cover"));
    }

    @Test
    void aWellMadeBookHasNothingToCheck() throws IOException {
        Path cover = image("cover.jpg", 1600, 2560);
        ChapterText chapter = new ChapterText("01-alba", "# Alba\n\n![La mappa](../media/cover.jpg)");
        BookExport book = BookAssembler.assemble(BOOK, Optional.of(cover), Optional.empty(),
                tempDir.resolve("chapters"), List.of(chapter), EmptyChapters.SKIP);

        assertThat(writer.check(book, EpubVariant.STORES)).isEmpty();
    }

    @Test
    void anIdentifierThatIsNotAUuidIsReported() {
        BookProperties properties = new BookProperties("Titolo", "", List.of(), "it-IT", "", "urn:uuid:1234-abcd");
        BookExport book = BookAssembler.assemble(properties, Optional.empty(), Optional.empty(), tempDir,
                List.of(new ChapterText("01-alba", "# Alba")), EmptyChapters.SKIP);

        assertThat(writer.check(book, EpubVariant.KINDLE))
                .containsExactly("The book's identifier, urn:uuid:1234-abcd, is not a valid UUID. Stores use it to "
                        + "tell this book from others: set it back to one Psiche Writer makes in "
                        + "media/book.properties.");
    }

    @Test
    void linksToAChapterOrHeadingLeadToItsPageAndPassEpubCheck() throws IOException {
        Path target = tempDir.resolve("links.epub");
        Files.createDirectories(tempDir.resolve("chapters"));
        BookExport book = BookAssembler.assemble(BOOK, Optional.empty(), Optional.empty(), tempDir.resolve("chapters"),
                List.of(new ChapterText("01-alba", "# Alba\n\n[Sera](03-sera.md), [la città](03-sera.md#la-città), "
                                + "[vuoto](02-vuoto.md) and [here](#notte).\n\n## Notte\n"),
                        new ChapterText("02-vuoto", ""),
                        new ChapterText("03-sera", "# Sera\n\n## La città\n")), EmptyChapters.SKIP);

        writer.write(book, EpubVariant.STORES, target);

        // 02-vuoto is left out, so 03-sera is the second chapter, and the link to the empty one is its text.
        assertThat(entry(target, "EPUB/xhtml/chapter-1.xhtml")).contains(
                "<a href=\"chapter-2.xhtml#chapter-2\">Sera</a>",
                "<a href=\"chapter-2.xhtml#chapter-2--la-città\">la città</a>, vuoto and ",
                "<a href=\"chapter-1.xhtml#chapter-1--notte\">here</a>", "<h2 id=\"chapter-1--notte\">");
        assertPassesEpubCheck(target);
    }

    @Test
    void putsTheNotesAtTheChaptersEndAsPopUpsAndPassesEpubCheck() throws IOException {
        Path target = tempDir.resolve("notes.epub");
        Files.createDirectories(tempDir.resolve("chapters"));
        BookExport book = BookAssembler.assemble(BOOK, Optional.empty(), Optional.empty(), tempDir.resolve("chapters"),
                List.of(new ChapterText("01-alba", "# Alba\n\nMoon[^m] and sun[^m].\n\n[^m]: A *full*\nmoon.\n\n"
                                + "    Second.\n"),
                        new ChapterText("02-sera", "# Sera\n\nEvening[^e].\n\n[^e]: Again one.\n")),
                EmptyChapters.SKIP);

        writer.write(book, EpubVariant.STORES, target);

        assertThat(entry(target, "EPUB/xhtml/chapter-1.xhtml")).contains(
                "Moon<sup><a id=\"chapter-1--noteref-1\" href=\"#chapter-1--note-1\" epub:type=\"noteref\" "
                        + "role=\"doc-noteref\">1</a></sup> and sun<sup><a href=\"#chapter-1--note-1\" "
                        + "epub:type=\"noteref\" role=\"doc-noteref\">1</a></sup>.",
                "<aside id=\"chapter-1--note-1\" epub:type=\"footnote\" role=\"doc-footnote\"><p>1. A <em>full</em>"
                        + "<br />moon.</p>",
                "<p>Second. <a href=\"#chapter-1--noteref-1\" role=\"doc-backlink\">↩</a></p></aside>");
        assertThat(entry(target, "EPUB/xhtml/chapter-2.xhtml")).contains("<p>1. Again one. <a");
        assertPassesEpubCheck(target);
    }

    /** Three chapters: one with a described image, a scene break and links; an empty one; one without a heading. */
    private BookExport book(Optional<Path> cover, EmptyChapters empty) throws IOException {
        image("map.png", 600, 400);
        return BookAssembler.assemble(BOOK, cover, Optional.empty(), tempDir.resolve("chapters"), List.of(
                new ChapterText("01-alba", "# Alba\n\nÈ così — «sì», *piano* & **forte**.\n\n---\n\n"
                        + "[Web](https://example.org) and [next](02-vuoto.md).\n\n![La mappa](../media/map.png)\n\n"
                        + "![La mappa](../media/big.png)\n\n<!-- a note -->\n\nRaw <b>html</b>."),
                new ChapterText("02-vuoto", ""),
                new ChapterText("03-sera", "No heading.\n\n![](../media/map.png)\n\n![](../media/big.png)\n\n"
                        + "![Persa](../media/persa.png)")), empty);
    }

    /** A blank image of the given size in {@code media/}, next to {@code chapters/}. */
    private Path image(String name, int width, int height) throws IOException {
        Path media = Files.createDirectories(tempDir.resolve("media"));
        Path file = media.resolve(name);
        String format = name.endsWith(".png") ? "png" : "jpg";
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, file.toFile());
        Files.createDirectories(tempDir.resolve("chapters"));
        return file;
    }

    /** Runs EPUBCheck as stores do, and fails with its messages on any fatal error, error or warning. */
    private void assertPassesEpubCheck(Path epub) throws IOException {
        Path report = tempDir.resolve(epub.getFileName() + ".json");
        int result = new EpubChecker().run(new String[] {epub.toString(), "--json", report.toString()});

        String json = Files.readString(report);
        String messages = json.substring(json.indexOf("\"messages\""), json.indexOf("\"customMessageFileName\""));
        assertThat(json).as(messages).contains("\"nFatal\" : 0", "\"nError\" : 0", "\"nWarning\" : 0");
        assertThat(result).as(messages).isZero();
    }

    private static String entry(Path epub, String name) throws IOException {
        return new String(bytes(epub, name), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(Path epub, String name) throws IOException {
        try (ZipFile zip = new ZipFile(epub.toFile()); InputStream in = zip.getInputStream(zip.getEntry(name))) {
            return in.readAllBytes();
        }
    }

    private static List<String> entries(Path epub) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(epub))) {
            List<String> names = new ArrayList<>();
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                names.add(entry.getName());
            return names;
        }
    }
}
