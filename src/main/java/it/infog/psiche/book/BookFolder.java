package it.infog.psiche.book;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.book.model.enums.PageMeasure;
import it.infog.psiche.common.FileErrors;
import it.infog.psiche.common.SafeFiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A book on disk: a directory holding {@code chapters/} with one Markdown file per chapter, and {@code media/}. Besides
 * the chapters' images, {@code media/} holds what an ebook of the book needs: its properties in
 * {@code book.properties}, the cover as {@code cover.jpg} or {@code cover.png}, and the ebook's {@code style.css}.
 * A book with chapter notes also has {@code notes/}, which Git leaves out.
 */
public final class BookFolder {

    static final String CHAPTERS_DIR = "chapters";
    static final String MEDIA_DIR = "media";
    static final String NOTES_DIR = "notes";
    static final String CHAPTER_EXTENSION = ".md";
    static final String PROPERTIES_FILE = "book.properties";
    static final String STYLE_FILE = "style.css";
    static final List<String> COVER_FILES = List.of("cover.jpg", "cover.png");

    private static final Logger log = LoggerFactory.getLogger(BookFolder.class);
    private static final String PAGE_COUNT = "pageCount";
    private static final String PROPERTIES_COMMENT = " Book properties, edited in Psiche Writer: Book properties…";

    private final Path root;

    private BookFolder(Path root) {

        this.root = root;
    }

    /**
     * @throws IllegalArgumentException if {@code directory} is not a directory or lacks {@code chapters/} or {@code media/}
     */
    public static BookFolder open(Path directory) {

        //preconditions to verify the directory is an actual book folder
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException(directory + " is not a directory");
        }

        List<String> missing = Stream.of(CHAPTERS_DIR, MEDIA_DIR)
                .filter(name -> !Files.isDirectory(directory.resolve(name)))
                .map(name -> name + "/")
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "%s is not a book folder: missing %s".formatted(directory, String.join(" and ", missing)));
        }

        return new BookFolder(directory.toAbsolutePath().normalize());
    }

    /**
     * Creates an empty book in a new directory named {@code title} inside {@code parent}.
     *
     * @param language the language the book is written in, as a BCP 47 tag such as {@code en-US}
     * @param author   the book's author; blank for none yet
     * @throws IllegalArgumentException if {@code title} is not a usable folder name or that folder already exists
     */
    public static BookFolder create(Path parent, String title, String language, String author) {

        Path root = resolveName(parent, title, "", "Book title", "folder");
        try {
            Files.createDirectory(root);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalArgumentException("There is already a folder named \"%s\" in %s: choose another title or"
                    .formatted(root.getFileName(), parent) + " another place.", e);
        } catch (IOException e) {
            throw cannotCreate(root, e);
        }

        // From here the folder is ours: if the book cannot be made whole, it goes, so a second try finds no half book.
        try {
            Files.createDirectory(root.resolve(CHAPTERS_DIR));
            Files.createDirectory(root.resolve(MEDIA_DIR));
            BookFolder book = open(root);
            BookProperties defaults = book.properties();
            List<String> authors = author.isBlank() ? List.of() : List.of(author.strip());
            book.saveProperties(new BookProperties(defaults.title(), defaults.subtitle(), authors, language,
                    defaults.rights(), defaults.identifier()));
            BookState.of(book);
            return book;
        } catch (IOException e) {
            removeHalfMade(root);
            throw cannotCreate(root, e);
        } catch (UncheckedIOException e) {
            removeHalfMade(root);
            throw cannotCreate(root, e.getCause());
        }
    }

    // Directories

    public Path root() {

        return root;
    }

    public Path chaptersDir() {

        return root.resolve(CHAPTERS_DIR);
    }

    public Path mediaDir() {

        return root.resolve(MEDIA_DIR);
    }

    /** {@code notes/}, the chapters' notes; it may not exist, as a book has it only once a chapter has notes. */
    public Path notesDir() {

        return root.resolve(NOTES_DIR);
    }

    public String title() {

        return root.getFileName().toString();
    }

    // Chapters

    /**
     * Creates an empty chapter file named {@code title} in {@code chapters/}. The {@code .md} extension is added
     * unless {@code title} already ends with it.
     *
     * @throws IllegalArgumentException if {@code title} is not a usable file name or that chapter already exists
     */
    public Chapter createChapter(String title) {

        return newChapter(title, "");
    }

    /**
     * Writes a chapter that was deleted back to {@code chapters/} as {@code title}, with its last text.
     *
     * @throws IllegalArgumentException if {@code title} is not a usable file name or that chapter already exists
     */
    public Chapter restoreChapter(String title, String markdown) {

        return newChapter(title, markdown);
    }

    /**
     * Renames {@code chapter} to {@code title}, under the same rules as {@link #createChapter}. A title that only
     * changes the letter case renames the file on case-insensitive file systems too.
     *
     * @return the chapter under its new name; {@code chapter} itself if the title is its current one
     * @throws IllegalArgumentException if {@code title} is not a usable file name or another chapter has it
     */
    public Chapter renameChapter(Chapter chapter, String title) {

        Path source = chapter.file();
        Path target = chapterFile(title);
        if (target.equals(source))
            return chapter;

        try {
            if (Files.exists(target) && !Files.isSameFile(source, target))
                throw new IllegalArgumentException("Chapter " + target.getFileName() + " already exists");

            if (Files.exists(target)) {
                // The same file under another letter case: moving it straight there would do nothing.
                Path temporary = source.resolveSibling(source.getFileName() + ".renaming");
                Files.move(source, temporary);
                Files.move(temporary, target);
            } else
                Files.move(source, target);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalArgumentException("Chapter " + target.getFileName() + " already exists", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot rename chapter " + source + " to " + target.getFileName(), e);
        }

        return new Chapter(target);
    }

    public void deleteChapter(Chapter chapter) {

        try {
            Files.delete(chapter.file());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete chapter " + chapter.file(), e);
        }
    }

    /**
     * The file in {@code chapters/} for a chapter titled {@code title}, with or without its extension, whether or not
     * it exists.
     *
     * @throws IllegalArgumentException if {@code title} is not a usable file name
     */
    public Path chapterFile(String title) {

        String name = title == null ? "" : title.strip();
        if (name.endsWith(CHAPTER_EXTENSION))
            name = name.substring(0, name.length() - CHAPTER_EXTENSION.length());

        return resolveName(chaptersDir(), name, CHAPTER_EXTENSION, "Chapter title", "file");
    }

    /** Chapters ordered by file name, so authors control the order with prefixes like {@code 01-}. */
    public List<Chapter> chapters() {

        try (Stream<Path> files = Files.list(chaptersDir())) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(CHAPTER_EXTENSION))
                    .sorted()
                    .map(Chapter::new)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list chapters in " + chaptersDir(), e);
        }
    }

    public String read(Chapter chapter) {

        try {
            return Files.readString(chapter.file());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + chapter.file(), e);
        }
    }

    public void write(Chapter chapter, String markdown) {

        try {
            Files.writeString(chapter.file(), markdown);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + chapter.file(), e);
        }
    }

    // Style and Cover

    /** The ebook's stylesheet, {@code media/style.css}, if the book has one. */
    public Optional<Path> stylesheet() {

        Path style = mediaDir().resolve(STYLE_FILE);
        return Files.isRegularFile(style) ? Optional.of(style) : Optional.empty();
    }

    /** The cover image, {@code media/cover.jpg} or {@code media/cover.png}; empty if the book has none. */
    public Optional<Path> cover() {

        return COVER_FILES.stream().map(mediaDir()::resolve).filter(Files::isRegularFile).findFirst();
    }

    /**
     * Copies {@code image} into {@code media/} as the cover, named {@code cover.jpg} or {@code cover.png} after its
     * type, and removes a cover of the other type.
     *
     * @throws IllegalArgumentException if {@code image} is not a JPEG or PNG file
     */
    public void setCover(Path image) {

        String name = image.getFileName().toString().toLowerCase(Locale.ROOT);
        String coverName;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
            coverName = "cover.jpg";
        else if (name.endsWith(".png"))
            coverName = "cover.png";
        else
            throw new IllegalArgumentException(image.getFileName() + " is not a JPEG or PNG image");

        Path cover = mediaDir().resolve(coverName);
        try {
            Files.copy(image, cover, StandardCopyOption.REPLACE_EXISTING);
            for (String other : COVER_FILES)
                if (!other.equals(coverName))
                    Files.deleteIfExists(mediaDir().resolve(other));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot use " + image + " as the cover", e);
        }
    }

    public void removeCover() {

        try {
            for (String cover : COVER_FILES)
                Files.deleteIfExists(mediaDir().resolve(cover));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot remove the cover from " + mediaDir(), e);
        }
    }

    // Measure

    /**
     * How the book's chapters are counted in pages, as last chosen; until then, the measure publishers use for the
     * book's language.
     */
    public PageMeasure pageMeasure() {

        return PageMeasure.fromKey(loadProperties().getProperty(PAGE_COUNT, ""))
                .orElseGet(() -> PageMeasure.forLanguage(properties().language()));
    }

    /** Keeps {@code measure} in {@code media/book.properties} with the book's other properties. */
    public void savePageMeasure(PageMeasure measure) {

        Properties saved = loadProperties();
        saved.setProperty(PAGE_COUNT, measure.key());
        storeProperties(saved);
    }

    // Properties

    /**
     * The book's properties as last saved. Anything not saved yet has a default: the title is the folder's name, the
     * language the system's, and the identifier a new one, which is kept once the properties are saved.
     */
    public BookProperties properties() {

        Properties saved = loadProperties();
        String language = Locale.getDefault().toLanguageTag();

        return new BookProperties(
                saved.getProperty("title", title()),
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
    public void saveProperties(BookProperties properties) {

        if (properties.title().isBlank())
            throw new IllegalArgumentException("Book title must not be blank");

        Properties saved = loadProperties();
        saved.setProperty("title", properties.title().strip());
        saved.setProperty("subtitle", properties.subtitle().strip());
        saved.setProperty("authors", properties.authors().stream().map(String::strip).collect(Collectors.joining("\n")));
        saved.setProperty("language", properties.language());
        saved.setProperty("rights", properties.rights().strip());
        saved.setProperty("identifier", properties.identifier());
        storeProperties(saved);

        ensureStyle();
    }

    private Chapter newChapter(String title, String markdown) {

        Path file = chapterFile(title);
        try {
            Files.writeString(file, markdown, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalArgumentException("Chapter " + file.getFileName() + " already exists", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create chapter " + file, e);
        }

        return new Chapter(file);
    }

    /** Resolves {@code name + suffix} against {@code parent}, accepting only a single, non-blank path element. */
    /** Why the book folder {@code root} cannot be created, in words the author can act on. */
    private static UncheckedIOException cannotCreate(Path root, IOException cause) {

        String reason = FileErrors.reason(cause);
        return new UncheckedIOException("Cannot create the book folder %s. %s".formatted(root, reason), cause);
    }

    /** Removes {@code root}, a book folder just created that could not be made whole; logged if it cannot. */
    private static void removeHalfMade(Path root) {

        try {
            SafeFiles.deleteTree(root);
        } catch (UncheckedIOException e) {
            log.warn("Cannot remove {}, a book folder left half made", root, e);
        }
    }

    private static Path resolveName(Path parent, String name, String suffix, String subject, String kind) {

        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty())
            throw new IllegalArgumentException(subject + " must not be blank");

        if (stripped.equals(".") || stripped.equals("..") || stripped.contains("/") || stripped.contains("\\"))
            throw new IllegalArgumentException("'%s' cannot be used as a %s name".formatted(stripped, kind));

        try {
            return parent.resolve(stripped + suffix);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("'%s' cannot be used as a %s name".formatted(stripped, kind), e);
        }
    }

    /** Writes {@code saved} to {@code media/book.properties}. */
    private void storeProperties(Properties saved) {

        //Properties.store adds a timestamp comment, which would change the file on every save; leave it out
        StringWriter text = new StringWriter();
        try {
            saved.store(text, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String entries = text.toString().lines().filter(line -> !line.startsWith("#"))
                .collect(Collectors.joining("\n", "#" + PROPERTIES_COMMENT + "\n", "\n"));

        Path file = mediaDir().resolve(PROPERTIES_FILE);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(entries);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + file, e);
        }
    }

    /** Writes the default ebook stylesheet to {@code media/style.css}, unless the book already has one. */
    private void ensureStyle() {

        Path style = mediaDir().resolve(STYLE_FILE);
        if (Files.exists(style))
            return;

        try (InputStream defaultStyle = BookFolder.class.getResourceAsStream("ebook.css")) {
            Files.copy(defaultStyle, style);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + style, e);
        }
    }

    private Properties loadProperties() {

        Properties properties = new Properties();
        Path file = mediaDir().resolve(PROPERTIES_FILE);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (NoSuchFileException e) {
            // A book without saved properties yet; every property has its default.
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        return properties;
    }
}
