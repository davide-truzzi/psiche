package it.infog.psiche.book;

import it.infog.psiche.book.model.ChapterPlace;
import it.infog.psiche.book.model.enums.NotesBoard;
import it.infog.psiche.common.PropertiesFiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * What Psiche remembers about one book, in {@value #FILE} at the book's root, which every book gets as it is created
 * or first opened, so a folder shows at a glance that it is a Psiche book: where the author left each chapter, and
 * which notes board, the chapter's or the book's, they looked at last.
 * It is the author's own state, not the book's, so it stays out of Git; a Psiche project takes it along.
 */
public final class BookState {

    public static final String FILE = "psiche.properties";

    private static final Logger log = LoggerFactory.getLogger(BookState.class);
    private static final String COMMENT = "Where you left each chapter and the notes, kept by Psiche Writer";
    private static final String FORMAT_KEY = "format";
    private static final String FORMAT = "1";
    private static final String CHAPTER_PREFIX = "chapter.";
    private static final String CARET = ".caret";
    private static final String TOP = ".top";
    private static final String NOTES_BOARD = "notes.board";

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

    /** The notes board the author looked at last, the chapter's or the book's; the chapter's at first. */
    public NotesBoard notesBoard() {

        String saved = properties.getProperty(NOTES_BOARD, "");
        return Arrays.stream(NotesBoard.values())
                .filter(board -> board.name().equalsIgnoreCase(saved))
                .findFirst()
                .orElse(NotesBoard.CHAPTER);
    }

    /** Remembers {@code board} as the notes board the author looked at last. */
    public void saveNotesBoard(NotesBoard board) {

        if (board == notesBoard())
            return;

        properties.setProperty(NOTES_BOARD, board.name().toLowerCase(Locale.ROOT));
        write();
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

    /** The properties in {@code file}; none, and logged, if they cannot be read: nothing depends on them. */
    private static Properties read(Path file) {

        try {
            return PropertiesFiles.readLeniently(file);
        } catch (UncheckedIOException e) {
            log.warn("Reading {} as empty: it cannot be read", file, e);
            return new Properties();
        }
    }

    /** Writes the file; if it cannot be, that is only logged: where chapters were left is not remembered. */
    private void write() {

        properties.setProperty(FORMAT_KEY, FORMAT);
        try {
            PropertiesFiles.write(file, properties, COMMENT);
        } catch (UncheckedIOException e) {
            log.warn("Cannot write {}; where chapters were left is not remembered", file, e);
        }
    }
}
