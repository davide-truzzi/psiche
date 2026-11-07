package it.infog.psiche.book;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.book.model.enums.PageMeasure;
import it.infog.psiche.common.ChapterFolders;
import it.infog.psiche.common.FileErrors;
import it.infog.psiche.common.SafeFiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A book on disk: a directory holding {@code chapters/} with one Markdown file per chapter, and {@code media/}. Besides
 * the chapters' images, {@code media/} holds what an ebook of the book needs: its properties in
 * {@code book.properties}, the cover as {@code cover.jpg} or {@code cover.png}, and the ebook's {@code style.css}.
 * A book with chapter notes also has {@code notes/}, which Git leaves out.
 */
public final class BookFolder {

    /**
     * The folder in {@code notes/} that holds the notes of the whole book, beside one folder per chapter; no chapter
     * may take its name.
     */
    public static final String BOOK_NOTES = "_book";

    static final String CHAPTERS_DIR = "chapters";
    static final String MEDIA_DIR = "media";

    /** The book's properties file, relative to the book folder with '/' separators, as Git names it. */
    public static final String PROPERTIES_PATH = MEDIA_DIR + "/" + BookMedia.PROPERTIES_FILE;
    /** The keys of the book's title and identifier in its properties file. */
    public static final String TITLE_KEY = BookMedia.TITLE_KEY;
    public static final String IDENTIFIER_KEY = BookMedia.IDENTIFIER_KEY;
    static final String NOTES_DIR = "notes";
    static final String CHAPTER_EXTENSION = ChapterFolders.CHAPTER_EXTENSION;

    private static final Logger log = LoggerFactory.getLogger(BookFolder.class);

    private final Path root;
    private final BookMedia media;

    private BookFolder(Path root) {

        this.root = root;
        media = new BookMedia(root.resolve(MEDIA_DIR), root.getFileName().toString());
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

            if (Files.exists(target))
                // The same file under another letter case.
                SafeFiles.changeCase(source, target);
            else
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
     * @throws IllegalArgumentException if {@code title} is not a usable file name, or is {@value #BOOK_NOTES}, which
     *                                  the book's own notes are kept under
     */
    public Path chapterFile(String title) {

        String name = title == null ? "" : title.strip();
        if (name.endsWith(CHAPTER_EXTENSION))
            name = name.substring(0, name.length() - CHAPTER_EXTENSION.length());
        // Any case: on Windows and macOS, "_Book" names the same folder.
        if (name.equalsIgnoreCase(BOOK_NOTES))
            throw new IllegalArgumentException(
                    "'%s' is the name the book's own notes are kept under: choose another title".formatted(name));

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

    // Style, cover and properties, in media/; see BookMedia

    /** The ebook's stylesheet, {@code media/style.css}, if the book has one. */
    public Optional<Path> stylesheet() {

        return media.stylesheet();
    }

    /** The cover image, {@code media/cover.jpg} or {@code media/cover.png}; empty if the book has none. */
    public Optional<Path> cover() {

        return media.cover();
    }

    /**
     * Copies {@code image} into {@code media/} as the cover, named after its type, in place of any other.
     *
     * @throws IllegalArgumentException if {@code image} is not a JPEG or PNG file
     */
    public void setCover(Path image) {

        media.setCover(image);
    }

    public void removeCover() {

        media.removeCover();
    }

    /** How the book's chapters are counted in pages; see {@link PageMeasure}. */
    public PageMeasure pageMeasure() {

        return media.pageMeasure();
    }

    public void savePageMeasure(PageMeasure measure) {

        media.savePageMeasure(measure);
    }

    /** The book's properties as last saved, with defaults for what is not saved yet. */
    public BookProperties properties() {

        return media.properties();
    }

    /**
     * Writes the properties to {@code media/book.properties}, and the default ebook stylesheet if the book has none.
     *
     * @throws IllegalArgumentException if the title is blank
     */
    public void saveProperties(BookProperties properties) {

        media.saveProperties(properties);
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
}
