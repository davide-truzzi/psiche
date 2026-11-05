package it.infog.psiche.notes;

import it.infog.psiche.common.PropertiesFiles;
import it.infog.psiche.common.SafeFiles;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NoteLayout;
import it.infog.psiche.notes.model.enums.NoteColor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import static it.infog.psiche.common.SafeFiles.isTemporary;
import static it.infog.psiche.common.SafeFiles.move;

/**
 * How a note is kept on disk, for {@link NoteStore}, {@link DetachedNoteStore} and {@link NotesArchive}: a folder
 * named by the note's id, with {@value #TEXT_FILE} for its text, {@value #PROPERTIES_FILE} for its place, size, colour,
 * tag and the name of its file, and the file beside them. Every file is written whole to a temporary file that then
 * takes its place, so a crash cannot leave half a note behind; temporary files, and folders whose name starts with a
 * dot, are never read as notes.
 */
final class NoteFolders {

    static final String TEXT_FILE = "note.md";
    static final String PROPERTIES_FILE = "note.properties";
    static final String BOARD_FILE = "board.properties";
    static final String DELETED_FILE = "deleted.properties";
    static final Set<String> OWN_FILES = Set.of(TEXT_FILE, PROPERTIES_FILE);
    static final String FILE_KEY = "file";
    static final String DUPLICATE_KEY = "duplicateOnImport";

    private static final Logger log = LoggerFactory.getLogger(NoteFolders.class);
    // Written beside the file they replace, and left out when a note's folder is read.
    // Folders whose names start with it are Psiche's own work, never notes: temporary files and import folders.
    private static final String HIDDEN_PREFIX = ".";
    private static final NoteLayout DEFAULT_LAYOUT = new NoteLayout(0, 0, 220, 160, 0, NoteColor.PAPER);

    private NoteFolders() {
    }

    // Notes

    /** The note in {@code folder}; empty, and logged, if it cannot be read: one damaged note never hides the rest. */
    static Optional<Note> readNote(Path folder) {

        try {
            Path textFile = folder.resolve(TEXT_FILE);
            String text = Files.exists(textFile) ? Files.readString(textFile) : "";
            Properties properties = PropertiesFiles.readLeniently(folder.resolve(PROPERTIES_FILE));

            boolean duplicate = Boolean.parseBoolean(properties.getProperty(DUPLICATE_KEY));
            return Optional.of(new Note(folder.getFileName().toString(), text, layout(properties), duplicate,
                    attachment(folder, properties)));
        } catch (IOException | UncheckedIOException e) {
            log.warn("Skipping the note in {}: it cannot be read", folder, e);
            return Optional.empty();
        }
    }

    /** The notes in {@code folder}, lowest in the stack first; one that cannot be read is left out. */
    static List<Note> readNotes(Path folder) {

        return subfolders(folder).stream()
                .map(NoteFolders::readNote)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt((Note note) -> note.layout().order()).thenComparing(Note::id))
                .toList();
    }

    /** A note's place, size, stacking order and colour, as its properties hold them; defaults where they don't. */
    static NoteLayout layout(Properties properties) {

        return new NoteLayout(
                number(properties, "x", DEFAULT_LAYOUT.x()),
                number(properties, "y", DEFAULT_LAYOUT.y()),
                number(properties, "width", DEFAULT_LAYOUT.width()),
                number(properties, "height", DEFAULT_LAYOUT.height()),
                (int) number(properties, "order", DEFAULT_LAYOUT.order()),
                NoteColor.fromKey(properties.getProperty("color", "")).orElse(DEFAULT_LAYOUT.color()));
    }

    /** Puts {@code layout} in a note's {@code properties}, with whatever else they hold. */
    static void putLayout(Properties properties, NoteLayout layout) {

        properties.setProperty("x", String.valueOf(layout.x()));
        properties.setProperty("y", String.valueOf(layout.y()));
        properties.setProperty("width", String.valueOf(layout.width()));
        properties.setProperty("height", String.valueOf(layout.height()));
        properties.setProperty("order", String.valueOf(layout.order()));
        properties.setProperty("color", layout.color().key());
    }

    /**
     * The name of the file a note carries, given its properties and the names of the files in its folder: the one its
     * properties name, even if it is gone; or, for a note given a file by hand, any but its own and temporary ones.
     */
    static Optional<String> attachmentName(Properties properties, Stream<String> names) {

        String recorded = properties.getProperty(FILE_KEY, "");
        if (!recorded.isEmpty() && !recorded.contains("/") && !recorded.contains("\\"))
            return Optional.of(recorded);

        return names.filter(name -> !OWN_FILES.contains(name))
                .filter(name -> !isTemporary(name))
                .sorted()
                .findFirst();
    }

    private static Optional<Path> attachment(Path folder, Properties properties) throws IOException {

        try (Stream<Path> entries = Files.list(folder)) {
            return attachmentName(properties,
                    entries.filter(Files::isRegularFile).map(file -> file.getFileName().toString()))
                    .map(folder::resolve);
        }
    }

    // Folders

    /** The folders directly in {@code folder}, by name, but those whose name starts with a dot; none if it is gone. */
    static List<Path> subfolders(Path folder) {

        if (!Files.isDirectory(folder))
            return List.of();

        try (Stream<Path> entries = Files.list(folder)) {
            return entries.filter(Files::isDirectory)
                    .filter(entry -> !entry.getFileName().toString().startsWith(HIDDEN_PREFIX))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the notes in " + folder, e);
        }
    }

    /** The folder of {@code chapter}'s notes in {@code root}, the book's {@code notes/}. */
    static Path chapterFolder(Path root, String chapter) {

        if (chapter.isBlank() || chapter.contains("/") || chapter.contains("\\") || chapter.equals("..")
                || chapter.equals("."))
            throw new IllegalArgumentException("'%s' cannot name a chapter's notes".formatted(chapter));

        return root.resolve(chapter);
    }

    /** Moves each note in {@code source} to {@code target}; one with an id {@code target} already has stays put. */
    static void moveNotes(Path source, Path target) throws IOException {

        Files.createDirectories(target);
        try (Stream<Path> entries = Files.list(source)) {
            for (Path note : entries.filter(Files::isDirectory).toList())
                if (!Files.exists(target.resolve(note.getFileName())))
                    move(note, target.resolve(note.getFileName()));
        }

        removeIfEmpty(source);
    }

    /**
     * Removes {@code folder} once it holds no note, with the files Psiche keeps in it; a file the author put there
     * keeps it, so nothing but Psiche's own files is ever lost.
     */
    static void removeIfEmpty(Path folder) throws IOException {

        if (!Files.isDirectory(folder))
            return;

        try (Stream<Path> entries = Files.list(folder)) {
            if (entries.anyMatch(Files::isDirectory))
                return;
        }
        Files.deleteIfExists(folder.resolve(BOARD_FILE));
        Files.deleteIfExists(folder.resolve(DELETED_FILE));

        try (Stream<Path> entries = Files.list(folder)) {
            if (entries.findAny().isEmpty())
                Files.delete(folder);
        }
    }

    // Properties and text files

    static double number(Properties properties, String key, double fallback) {

        try {
            return Double.parseDouble(properties.getProperty(key, ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Writes {@code text} to a temporary file beside {@code file}, which then takes its place. */
    static void writeAtomically(Path file, String text) {

        try {
            SafeFiles.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + file, e);
        }
    }
}
