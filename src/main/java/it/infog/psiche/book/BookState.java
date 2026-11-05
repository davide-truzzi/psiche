package it.infog.psiche.book;

import it.infog.psiche.book.model.ChapterPlace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import static it.infog.psiche.common.SafeFiles.move;
import static it.infog.psiche.common.SafeFiles.temporaryBeside;

/**
 * What Psiche remembers about one book, in {@value #FILE} at the book's root, which every book gets as it is created
 * or first opened, so a folder shows at a glance that it is a Psiche book: for now, where the author left each chapter.
 * It is the author's own state, not the book's, so it stays out of Git; a Psiche project takes it along.
 */
public final class BookState {

    public static final String FILE = "psiche.properties";

    private static final Logger log = LoggerFactory.getLogger(BookState.class);
    private static final String FORMAT_KEY = "format";
    private static final String FORMAT = "1";
    private static final String CHAPTER_PREFIX = "chapter.";
    private static final String CARET = ".caret";
    private static final String TOP = ".top";

    private final Path file;
    private final Path chaptersDir;
    private final Properties properties;

    private BookState(Path file, Path chaptersDir, Properties properties) {

        this.file = file;
        this.chaptersDir = chaptersDir;
        this.properties = properties;
    }

    /**
     * The state of {@code book}, writing {@value #FILE} if the book has none yet. A file that cannot be read counts as
     * empty, and one that cannot be written is only logged: nothing of the book depends on it.
     */
    public static BookState of(BookFolder book) {

        Path file = book.root().resolve(FILE);
        BookState state = new BookState(file, book.chaptersDir(), read(file));
        if (!Files.exists(file))
            state.write();
        return state;
    }

    /** Where the author left {@code chapter}; empty if it was never opened, or the place cannot be read. */
    public Optional<ChapterPlace> place(Chapter chapter) {

        String key = CHAPTER_PREFIX + chapter.title();
        try {
            String caret = properties.getProperty(key + CARET);
            String top = properties.getProperty(key + TOP);
            if (caret == null || top == null)
                return Optional.empty();

            return Optional.of(new ChapterPlace(Integer.parseInt(caret), Integer.parseInt(top)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Remembers {@code place} for {@code chapter}, if it is still there, and writes the file; places kept for chapters
     * no longer in the book are dropped meanwhile.
     */
    public void savePlace(Chapter chapter, ChapterPlace place) {

        if (Files.exists(chapter.file())) {
            String key = CHAPTER_PREFIX + chapter.title();
            properties.setProperty(key + CARET, String.valueOf(place.caret()));
            properties.setProperty(key + TOP, String.valueOf(place.topParagraph()));
        }
        dropGoneChapters();
        write();
    }

    /** Moves the place kept for {@code from} to {@code to}, a chapter just renamed. */
    public void rename(Chapter from, Chapter to) {

        Optional<ChapterPlace> place = place(from);
        properties.remove(CHAPTER_PREFIX + from.title() + CARET);
        properties.remove(CHAPTER_PREFIX + from.title() + TOP);
        place.ifPresentOrElse(kept -> savePlace(to, kept), this::write);
    }

    private void dropGoneChapters() {

        properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(CHAPTER_PREFIX) && (key.endsWith(CARET) || key.endsWith(TOP)))
                .filter(key -> {
                    String title = key.substring(CHAPTER_PREFIX.length(), key.lastIndexOf('.'));
                    return !Files.exists(chaptersDir.resolve(title + BookFolder.CHAPTER_EXTENSION));
                })
                .forEach(properties::remove);
    }

    private static Properties read(Path file) {

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (NoSuchFileException e) {
            // A book not opened by this Psiche before: it gets the file now.
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Reading {} as empty: it cannot be read", file, e);
            properties.clear();
        }
        return properties;
    }

    /** Writes the file whole, keys in order and with no timestamp, through a temporary file beside it. */
    private void write() {

        properties.setProperty(FORMAT_KEY, FORMAT);
        StringWriter text = new StringWriter();
        try {
            properties.store(text, null);
            String entries = text.toString().lines()
                    .filter(line -> !line.startsWith("#"))
                    .sorted()
                    .collect(Collectors.joining("\n", "# Where you left each chapter, kept by Psiche Writer\n", "\n"));
            Path temporary = temporaryBeside(file);
            try {
                Files.writeString(temporary, entries, StandardCharsets.UTF_8);
                move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | UncheckedIOException e) {
            log.warn("Cannot write {}; where chapters were left is not remembered", file, e);
        }
    }
}
