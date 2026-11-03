package it.infog.psiche.export.epub;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.Template;
import it.infog.psiche.export.epub.model.EpubImage;
import it.infog.psiche.export.epub.model.EpubPage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static it.infog.psiche.export.XmlText.escape;

/**
 * The package document, {@code package.opf}: what the book is (its metadata), what the EPUB holds (the manifest) and
 * in which order it is read (the spine). Metadata values carry no surrounding whitespace, which Apple Books asks for.
 */
final class PackageDocument {

    static final String PATH = "package.opf";

    private final Template opf = Template.load(PackageDocument.class, "package.opf");

    /**
     * @param identifier the book's unique identifier, such as a {@code urn:uuid}, the same for every release
     * @param language   a BCP 47 tag such as {@code it-IT}
     * @param modified   when this file was made, which tells this release of the book from earlier ones
     * @param spine      the pages, in reading order
     * @param cover      the cover image, if the EPUB has one
     * @param images     every image packed, the cover included
     * @param described  true if every image has a text description
     */
    String write(BookProperties properties, String identifier, String language, Instant modified,
                 List<EpubPage> spine, Optional<EpubImage> cover, List<EpubImage> images, boolean described) {

        return opf.fill(Map.of(
                "language", escape(language),
                "metadata", metadata(properties, identifier, language, modified, cover.isPresent())
                        + Accessibility.metadata(!images.isEmpty(), described),
                "manifest", manifest(spine, cover, images),
                "spine", spine(spine)));
    }

    private static String metadata(BookProperties properties, String identifier, String language, Instant modified,
                                   boolean hasCover) {

        StringBuilder meta = new StringBuilder();
        meta.append(line("<dc:identifier id=\"book-id\">%s</dc:identifier>", identifier));

        //title and subtitle
        meta.append(line("<dc:title id=\"title\">%s</dc:title>", properties.title()));
        meta.append(line("<meta refines=\"#title\" property=\"title-type\">main</meta>"));
        if (!properties.subtitle().isBlank()) {
            meta.append(line("<dc:title id=\"subtitle\">%s</dc:title>", properties.subtitle()));
            meta.append(line("<meta refines=\"#subtitle\" property=\"title-type\">subtitle</meta>"));
        }

        //authors, in the order they are credited
        for (int i = 0; i < properties.authors().size(); i++) {
            String id = "author-" + (i + 1);
            meta.append(line("<dc:creator id=\"" + id + "\">%s</dc:creator>", properties.authors().get(i)));
            meta.append(line("<meta refines=\"#%s\" property=\"role\" scheme=\"marc:relators\">aut</meta>"
                    .formatted(id)));
        }

        //language and copyright
        meta.append(line("<dc:language>%s</dc:language>", language));
        if (!properties.rights().isBlank())
            meta.append(line("<dc:rights>%s</dc:rights>", properties.rights()));
        meta.append(line("<meta property=\"dcterms:modified\">%s</meta>",
                modified.truncatedTo(ChronoUnit.SECONDS).toString()));

        // EPUB 2's way of naming the cover, which Kindle and older e-readers read.
        if (hasCover)
            meta.append(line("<meta name=\"cover\" content=\"cover-image\"/>"));

        return meta.toString();
    }

    private static String manifest(List<EpubPage> spine, Optional<EpubImage> cover, List<EpubImage> images) {

        StringBuilder items = new StringBuilder();
        items.append(item("ncx", Navigation.NCX_PATH, "application/x-dtbncx+xml", ""));

        for (EpubPage page : spine)
            items.append(item(page.id(), page.path(), "application/xhtml+xml", page.properties()));

        items.append(item("book-style", Pages.BOOK_STYLE, "text/css", ""));
        items.append(item("front-matter-style", Pages.FRONT_MATTER_STYLE, "text/css", ""));

        for (EpubImage image : images) {
            boolean isCover = cover.filter(image::equals).isPresent();
            items.append(item(isCover ? "cover-image" : id(image), image.path(), image.mediaType(),
                    isCover ? "cover-image" : ""));
        }

        return items.toString();
    }

    private static String spine(List<EpubPage> spine) {

        StringBuilder itemrefs = new StringBuilder();
        for (EpubPage page : spine)
            itemrefs.append("        <itemref idref=\"%s\"/>\n".formatted(page.id()));

        return itemrefs.toString();
    }

    private static String item(String id, String href, String mediaType, String properties) {

        String property = properties.isEmpty() ? "" : " properties=\"%s\"".formatted(properties);
        return "        <item id=\"%s\" href=\"%s\" media-type=\"%s\"%s/>\n".formatted(id, href, mediaType, property);
    }

    /** An image's id: its file name without the extension, such as {@code image-2}. */
    private static String id(EpubImage image) {

        String name = image.path().substring(image.path().lastIndexOf('/') + 1);
        return name.substring(0, name.lastIndexOf('.'));
    }

    /** A metadata element, with {@code value} escaped and stripped into its {@code %s}. */
    private static String line(String element, String value) {

        return line(element.formatted(escape(value.strip())));
    }

    private static String line(String element) {

        return "        " + element + "\n";
    }
}
