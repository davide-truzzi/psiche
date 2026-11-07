package it.infog.psiche.export.epub;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.common.Template;
import it.infog.psiche.export.LinkTargets;
import it.infog.psiche.export.XhtmlRenderer;
import it.infog.psiche.export.ZipContainer;
import it.infog.psiche.export.epub.model.EpubImage;
import it.infog.psiche.export.epub.model.EpubPage;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ExportedChapter;
import it.infog.psiche.export.model.enums.EpubVariant;
import it.infog.psiche.markdown.PlainText;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes the whole book as an EPUB 3 ready for the stores, which checks clean with EPUBCheck, the validator stores
 * run. Its reading order is:
 * <ol>
 *     <li>the cover, full screen (not for Kindle, which adds its own);</li>
 *     <li>the title page;</li>
 *     <li>the contents, a link per chapter;</li>
 *     <li>the chapters, a page each, set by the book's own stylesheet;</li>
 *     <li>the copyright notice, at the back, so a store's sample of the book shows as much of the text as it can.</li>
 * </ol>
 * The package also holds {@code toc.ncx} for EPUB 2 e-readers, and the accessibility metadata EPUB Accessibility 1.1
 * asks for. Fonts are not embedded: e-readers use their own, and every megabyte costs on Kindle.
 */
public final class EpubWriter {

    private static final String MIMETYPE = "application/epub+zip";
    // The stylesheet Psiche Writer gives every new book, for a book that has none of its own.
    private static final String DEFAULT_STYLE = "/it/infog/psiche/book/ebook.css";
    // Stores ask for a cover at least this many pixels on its short side (Apple Books).
    private static final int COVER_MIN_SIDE = 1400;

    private final Template container = Template.load(EpubWriter.class, "container.xml");
    private final Template frontMatterStyle = Template.load(EpubWriter.class, "front-matter.css");
    private final PackageDocument packageDocument = new PackageDocument();
    private final Navigation navigation = new Navigation();
    private final Clock clock;

    public EpubWriter() {

        this(Clock.systemUTC());
    }

    /** @param clock when the file is made, for its {@code dcterms:modified} */
    EpubWriter(Clock clock) {

        this.clock = clock;
    }

    /**
     * What would make the EPUB fall short of what stores ask for, for the author to fix or accept before exporting:
     * a missing or small cover, images left out, images without a description.
     *
     * @return a sentence per problem; empty if there is none
     */
    public List<String> check(BookExport book, EpubVariant variant) {

        List<String> problems = new ArrayList<>();
        if (variant == EpubVariant.STORES)
            checkCover(book.titlePage().cover()).ifPresent(problems::add);
        checkIdentifier(book.properties().identifier()).ifPresent(problems::add);

        //images
        EpubImages images = new EpubImages(book.baseDirectory());
        int undescribed = 0;
        for (Image image : images(book))
            if (images.image(image.getDestination()).isPresent() && PlainText.of(image).isBlank())
                undescribed++;

        if (!images.leftOut().isEmpty())
            problems.add("These images will be left out, as e-readers could not show them:\n"
                    + String.join("\n", images.leftOut()));
        if (undescribed > 0)
            problems.add(("%d %s no description, the text in square brackets of ![description](image), which "
                    + "readers who cannot see them would hear instead.").formatted(undescribed,
                    undescribed == 1 ? "image has" : "images have"));

        return problems;
    }

    /**
     * Writes the EPUB.
     *
     * @throws IllegalArgumentException if the book has no chapters
     */
    public void write(BookExport book, EpubVariant variant, Path target) {

        if (book.chapters().isEmpty())
            throw new IllegalArgumentException("The book has no chapters to export");

        BookProperties properties = book.properties();
        String language = properties.language().isBlank() ? "und" : properties.language().strip();
        String identifier = identifier(properties);

        //pages, in reading order
        EpubImages images = new EpubImages(book.baseDirectory());
        Pages pages = new Pages(language);
        Optional<EpubImage> cover = variant == EpubVariant.STORES
                ? book.titlePage().cover().flatMap(images::cover)
                : Optional.empty();
        Optional<EpubPage> coverPage = cover.map(image -> pages.cover(image, properties.title()));
        EpubPage titlePage = pages.titlePage(book.titlePage());

        // A place of the book is on the page of its chapter.
        XhtmlRenderer renderer = XhtmlRenderer.ebook(images,
                place -> Pages.fileName(place.chapterId()) + "#" + place.elementId());
        LinkTargets links = LinkTargets.of(book.chapters());
        List<EpubPage> chapters = book.chapters().stream().map(chapter -> pages.chapter(chapter, renderer, links))
                .toList();
        EpubPage contents = navigation.contents(pages, book.contents(), coverPage, titlePage);

        List<EpubPage> spine = new ArrayList<>();
        coverPage.ifPresent(spine::add);
        spine.add(titlePage);
        spine.add(contents);
        spine.addAll(chapters);
        if (!properties.rights().isBlank())
            spine.add(pages.copyright(properties.rights(), properties.title()));

        //package
        String opf = packageDocument.write(properties, identifier, language, clock.instant(), spine, cover,
                images.all(), everyImageDescribed(book, images));
        String ncx = navigation.ncx(book.contents(), identifier, properties.title(), language);

        try (ZipContainer epub = new ZipContainer(target, MIMETYPE)) {
            epub.addText("META-INF/container.xml", container.fill(Map.of()));
            epub.addText("EPUB/" + PackageDocument.PATH, opf);
            epub.addText("EPUB/" + Navigation.NCX_PATH, ncx);
            for (EpubPage page : spine)
                epub.addText("EPUB/" + page.path(), page.xhtml());
            epub.addBytes("EPUB/" + Pages.BOOK_STYLE, bookStyle(book.stylesheet()));
            epub.addText("EPUB/" + Pages.FRONT_MATTER_STYLE, frontMatterStyle.fill(Map.of()));
            for (EpubImage image : images.all())
                epub.addBytes("EPUB/" + image.path(), EpubImages.bytes(image));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + target, e);
        }
    }

    /** What is wrong with the cover for the stores, if anything. */
    private static Optional<String> checkCover(Optional<Path> cover) {

        if (cover.isEmpty())
            return Optional.of(
                    "The book has no cover, which stores require. Add one with Book properties… in the Book menu.");

        BufferedImage image;
        try {
            image = ImageIO.read(cover.get().toFile());
        } catch (IOException e) {
            image = null;
        }

        if (image == null)
            return Optional.of("The cover cannot be read. Save it as an RGB JPEG or PNG image.");

        int shortSide = Math.min(image.getWidth(), image.getHeight());
        if (shortSide < COVER_MIN_SIDE)
            return Optional.of(("The cover is %d × %d pixels. Stores ask for at least %d pixels on its short side; "
                    + "Amazon recommends 1600 × 2560.").formatted(image.getWidth(), image.getHeight(), COVER_MIN_SIDE));

        return Optional.empty();
    }

    /** What is wrong with the book's identifier, if anything: a {@code urn:uuid} must hold a valid UUID. */
    private static Optional<String> checkIdentifier(String identifier) {

        String uuid = identifier.strip();
        if (!uuid.regionMatches(true, 0, "urn:uuid:", 0, 9))
            return Optional.empty();

        try {
            UUID.fromString(uuid.substring(9));
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.of(("The book's identifier, %s, is not a valid UUID. Stores use it to tell this book from "
                    + "others: set it back to one Psiche Writer makes in media/book.properties.").formatted(uuid));
        }
    }

    /** True if every image the EPUB packs, the cover aside, has a description; the cover page names the book. */
    private static boolean everyImageDescribed(BookExport book, EpubImages images) {

        return images(book).stream()
                .filter(image -> images.image(image.getDestination()).isPresent())
                .noneMatch(image -> PlainText.of(image).isBlank());
    }

    /** Every image the chapters show, in order. */
    private static List<Image> images(BookExport book) {

        List<Image> images = new ArrayList<>();
        AbstractVisitor collector = new AbstractVisitor() {
            @Override
            public void visit(Image image) {
                images.add(image);
            }
        };
        for (ExportedChapter chapter : book.chapters())
            chapter.document().accept(collector);

        return images;
    }

    /** The book's identifier; one made from its title for a book that has none, so it stays the same every time. */
    private static String identifier(BookProperties properties) {

        if (!properties.identifier().isBlank())
            return properties.identifier().strip();

        return "urn:uuid:" + UUID.nameUUIDFromBytes(properties.title().getBytes(StandardCharsets.UTF_8));
    }

    /** The book's own stylesheet, or Psiche Writer's default one for a book that has none. */
    private static byte[] bookStyle(Optional<Path> stylesheet) throws IOException {

        if (stylesheet.isPresent())
            return Files.readAllBytes(stylesheet.get());

        try (InputStream in = EpubWriter.class.getResourceAsStream(DEFAULT_STYLE)) {
            return in.readAllBytes();
        }
    }
}
