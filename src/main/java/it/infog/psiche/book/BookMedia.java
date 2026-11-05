package it.infog.psiche.book;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.book.model.enums.PageMeasure;
import it.infog.psiche.common.PropertiesFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a book keeps in {@code media/} besides its images, for {@link BookFolder}: its properties, in
 * {@value #PROPERTIES_FILE}, with how its pages are counted, its cover, and the ebook's stylesheet.
 */
final class BookMedia {

    static final String PROPERTIES_FILE = "book.properties";
    static final String STYLE_FILE = "style.css";
    static final List<String> COVER_FILES = List.of("cover.jpg", "cover.png");

    private static final String PAGE_COUNT = "pageCount";
    private static final String PROPERTIES_COMMENT = "Book properties, edited in Psiche Writer: Book properties…";

    private final Path folder;
    private final String folderName;

    /** @param folderName the book folder's name, the title until one is saved */
    BookMedia(Path folder, String folderName) {

        this.folder = folder;
        this.folderName = folderName;
    }

    // Style and cover

    /** The ebook's stylesheet, {@code media/style.css}, if the book has one. */
    Optional<Path> stylesheet() {

        Path style = folder.resolve(STYLE_FILE);
        return Files.isRegularFile(style) ? Optional.of(style) : Optional.empty();
    }

    /** The cover image, {@code media/cover.jpg} or {@code media/cover.png}; empty if the book has none. */
    Optional<Path> cover() {

        return COVER_FILES.stream().map(folder::resolve).filter(Files::isRegularFile).findFirst();
    }

    /**
     * Copies {@code image} into {@code media/} as the cover, named {@code cover.jpg} or {@code cover.png} after its
     * type, and removes a cover of the other type.
     *
     * @throws IllegalArgumentException if {@code image} is not a JPEG or PNG file
     */
    void setCover(Path image) {

        String name = image.getFileName().toString().toLowerCase(Locale.ROOT);
        String coverName;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
            coverName = "cover.jpg";
        else if (name.endsWith(".png"))
            coverName = "cover.png";
        else
            throw new IllegalArgumentException(image.getFileName() + " is not a JPEG or PNG image");

        Path cover = folder.resolve(coverName);
        try {
            Files.copy(image, cover, StandardCopyOption.REPLACE_EXISTING);
            for (String other : COVER_FILES)
                if (!other.equals(coverName))
                    Files.deleteIfExists(folder.resolve(other));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot use " + image + " as the cover", e);
        }
    }

    void removeCover() {

        try {
            for (String cover : COVER_FILES)
                Files.deleteIfExists(folder.resolve(cover));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot remove the cover from " + folder, e);
        }
    }

    // Measure

    /**
     * How the book's chapters are counted in pages, as last chosen; until then, the measure publishers use for the
     * book's language.
     */
    PageMeasure pageMeasure() {

        return PageMeasure.fromKey(loadProperties().getProperty(PAGE_COUNT, ""))
                .orElseGet(() -> PageMeasure.forLanguage(properties().language()));
    }

    /** Keeps {@code measure} in {@code media/book.properties} with the book's other properties. */
    void savePageMeasure(PageMeasure measure) {

        Properties saved = loadProperties();
        saved.setProperty(PAGE_COUNT, measure.key());
        storeProperties(saved);
    }

    // Properties

    /**
     * The book's properties as last saved. Anything not saved yet has a default: the title is the folder's name, the
     * language the system's, and the identifier a new one, which is kept once the properties are saved.
     */
    BookProperties properties() {

        Properties saved = loadProperties();
        String language = Locale.getDefault().toLanguageTag();

        return new BookProperties(
                saved.getProperty("title", folderName),
                saved.getProperty("subtitle", ""),
                Arrays.stream(saved.getProperty("authors", "").split("\n")).filter(name -> !name.isBlank()).toList(),
                saved.getProperty("language", language.equals("und") ? "en" : language),
                saved.getProperty("rights", ""),
                saved.getProperty("identifier", "urn:uuid:" + UUID.randomUUID()));
    }

    /**
     * Writes the properties to {@code media/book.properties}, keeping any other entries the file has, and puts the
     * default ebook stylesheet in {@code media/} if it has none.
     *
     * @throws IllegalArgumentException if the title is blank
     */
    void saveProperties(BookProperties properties) {

        if (properties.title().isBlank())
            throw new IllegalArgumentException("Book title must not be blank");

        Properties saved = loadProperties();
        saved.setProperty("title", properties.title().strip());
        saved.setProperty("subtitle", properties.subtitle().strip());
        saved.setProperty("authors",
                properties.authors().stream().map(String::strip).collect(Collectors.joining("\n")));
        saved.setProperty("language", properties.language());
        saved.setProperty("rights", properties.rights().strip());
        saved.setProperty("identifier", properties.identifier());
        storeProperties(saved);

        ensureStyle();
    }

    /** Writes {@code saved} to {@code media/book.properties}. */
    private void storeProperties(Properties saved) {

        PropertiesFiles.write(folder.resolve(PROPERTIES_FILE), saved, PROPERTIES_COMMENT);
    }

    /** Writes the default ebook stylesheet to {@code media/style.css}, unless the book already has one. */
    private void ensureStyle() {

        Path style = folder.resolve(STYLE_FILE);
        if (Files.exists(style))
            return;

        try (InputStream defaultStyle = BookMedia.class.getResourceAsStream("ebook.css")) {
            Files.copy(defaultStyle, style);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + style, e);
        }
    }

    /** The saved properties; none if the book has none yet. */
    private Properties loadProperties() {

        return PropertiesFiles.read(folder.resolve(PROPERTIES_FILE));
    }
}
