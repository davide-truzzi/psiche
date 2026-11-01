package it.infog.psiche.notes;

import it.infog.psiche.notes.model.BoardView;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NoteLayout;
import it.infog.psiche.notes.model.OrphanGroup;
import it.infog.psiche.notes.model.enums.OrphanCause;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static it.infog.psiche.notes.NoteFolders.BOARD_FILE;
import static it.infog.psiche.notes.NoteFolders.DELETED_FILE;
import static it.infog.psiche.notes.NoteFolders.DUPLICATE_KEY;
import static it.infog.psiche.notes.NoteFolders.FILE_KEY;
import static it.infog.psiche.notes.NoteFolders.OWN_FILES;
import static it.infog.psiche.notes.NoteFolders.PROPERTIES_FILE;
import static it.infog.psiche.notes.NoteFolders.TEXT_FILE;
import static it.infog.psiche.notes.NoteFolders.copy;
import static it.infog.psiche.notes.NoteFolders.deleteTree;
import static it.infog.psiche.notes.NoteFolders.move;
import static it.infog.psiche.notes.NoteFolders.number;
import static it.infog.psiche.notes.NoteFolders.putLayout;
import static it.infog.psiche.notes.NoteFolders.readNote;
import static it.infog.psiche.notes.NoteFolders.readNotes;
import static it.infog.psiche.notes.NoteFolders.readProperties;
import static it.infog.psiche.notes.NoteFolders.subfolders;
import static it.infog.psiche.notes.NoteFolders.writeAtomically;
import static it.infog.psiche.notes.NoteFolders.writeProperties;

/**
 * A book's chapter notes, kept in {@code notes/} at the book's root, outside Git. Each chapter with notes has a folder
 * named like the chapter's file, without {@code .md}, holding one folder per note, named by the note's id: in it,
 * {@code note.md} holds the text, {@code note.properties} the note's place, size, colour and tag and the name
 * of the file it carries, and the file itself sits beside them. The chapter's folder also keeps
 * {@code board.properties}, where its board was last looked at.
 * <p>
 * Nothing here is destroyed but by {@link #deletePermanently}. A deleted note, or the notes of a deleted chapter, move
 * to a folder named like the chapter with {@value #DELETED_MARK} and the time added; it matches no chapter, so it is an
 * orphan, as is a folder whose chapter was renamed or deleted outside Psiche. A file a note no longer carries, replaced
 * or removed, goes there too, with a copy of the note. How each note is kept on disk is {@link NoteFolders}'.
 */
public final class NoteStore {

    static final String DELETED_MARK = "-del-";

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DELETED_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final Pattern DELETED_FOLDER =
            Pattern.compile("(.+)" + DELETED_MARK + "(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})");

    /** Hears how far a copy has come, on the thread that copies. */
    @FunctionalInterface
    public interface CopyProgress {

        void copied(long bytes, long total);
    }

    private final Path root;
    private final Clock clock;
    private final Random random = new Random();
    // Held while a note's properties are read, changed and written back: files are copied in on a thread of their
    // own, which records them there while the notes thread saves the note's place.
    private final Object propertiesLock = new Object();

    /** @param root the book's {@code notes/} folder, which may not exist yet */
    public NoteStore(Path root) {

        this(root, Clock.systemDefaultZone());
    }

    /** @param clock the time ids and deleted folders are named after, in its zone */
    NoteStore(Path root, Clock clock) {

        this.root = root;
        this.clock = clock;
    }

    /** The book's {@code notes/} folder, which may not exist yet. */
    Path root() {

        return root;
    }

    /** True while the book has no {@code notes/}, as before its first note. */
    public boolean isEmpty() {

        return !Files.isDirectory(root);
    }

    // A chapter's notes

    /** The notes of {@code chapter}, lowest in the stack first; one that cannot be read is left out. */
    public List<Note> notes(String chapter) {

        return readNotes(chapterFolder(chapter));
    }

    /**
     * Adds an empty note to {@code chapter}'s board at {@code layout}, creating the chapter's folder if it has none.
     *
     * @return the note, with its new id
     */
    public Note create(String chapter, NoteLayout layout) {

        Path folder = newNoteFolder(chapterFolder(chapter));
        String id = folder.getFileName().toString();

        writeAtomically(folder.resolve(TEXT_FILE), "");
        saveLayout(chapter, id, layout);
        return new Note(id, "", layout, false, Optional.empty());
    }

    /**
     * A new, empty folder for a note in {@code chapterFolder}, named by a new id: the time, which sorts notes by age,
     * and a random part, which keeps two made in the same second apart; an id taken already is drawn again.
     */
    Path newNoteFolder(Path chapterFolder) {

        try {
            Files.createDirectories(chapterFolder);
            while (true) {
                String id = LocalDateTime.now(clock).format(ID_TIME) + "-"
                        + HexFormat.of().toHexDigits((short) random.nextInt());
                try {
                    return Files.createDirectory(chapterFolder.resolve(id));
                } catch (FileAlreadyExistsException e) {
                    // Drawn twice in the same second: draw again.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create a note in " + chapterFolder, e);
        }
    }

    public void saveText(String chapter, String id, String text) {

        writeAtomically(noteFolder(chapter, id).resolve(TEXT_FILE), text);
    }

    /** Keeps {@code layout} in the note's properties, with whatever else they hold. */
    public void saveLayout(String chapter, String id, NoteLayout layout) {

        updateProperties(noteFolder(chapter, id), properties -> putLayout(properties, layout));
    }

    /** Marks the note as added by an import beside another version of it, or clears the mark. */
    public void setDuplicateOnImport(String chapter, String id, boolean duplicate) {

        updateProperties(noteFolder(chapter, id), properties -> {
            if (duplicate)
                properties.setProperty(DUPLICATE_KEY, "true");
            else
                properties.remove(DUPLICATE_KEY);
        });
    }

    // A note's file

    /**
     * Copies {@code file} into the note, under its own name, as the file it carries; a file it carried before goes to
     * Deleted notes, with a copy of the note. The copy is written beside its place and moved there once whole, so an
     * interrupted copy never shows as the note's file.
     *
     * @return the file as the note now carries it
     */
    public Path attach(String chapter, String id, Path file, CopyProgress progress) {

        Path folder = noteFolder(chapter, id);
        binFile(chapter, id);

        Path target = folder.resolve(attachmentName(file.getFileName().toString()));
        try (InputStream in = Files.newInputStream(file)) {
            copy(in, Files.size(file), target, progress);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot copy " + file + " into the note", e);
        }

        updateProperties(folder, properties -> properties.setProperty(FILE_KEY, target.getFileName().toString()));
        return target;
    }

    /** Takes the note's file away, to Deleted notes with a copy of the note; the note keeps its text. */
    public void removeAttachment(String chapter, String id) {

        binFile(chapter, id);
        updateProperties(noteFolder(chapter, id), properties -> properties.remove(FILE_KEY));
    }

    /** Moves the note's file, if it has one on disk, to a copy of the note in Deleted notes. */
    private void binFile(String chapter, String id) {

        Path folder = noteFolder(chapter, id);
        Optional<Path> file = readNote(folder).flatMap(Note::attachment).filter(Files::exists);
        if (file.isEmpty())
            return;

        Path bin = binFolder(chapter);
        try {
            Files.createDirectories(bin);
            Path copy = newNoteFolder(bin);
            for (String own : OWN_FILES)
                if (Files.exists(folder.resolve(own)))
                    Files.copy(folder.resolve(own), copy.resolve(own));
            Files.move(file.get(), copy.resolve(file.get().getFileName()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot keep the file of the note " + folder + " in " + bin, e);
        }
        if (!Files.exists(bin.resolve(DELETED_FILE)))
            markDeleted(bin, OrphanCause.NOTE_DELETED);
    }

    /** {@code name}, or, if it is the name of a note's own file, the same with " (1)" before its extension. */
    private static String attachmentName(String name) {

        if (!OWN_FILES.contains(name))
            return name;

        int dot = name.lastIndexOf('.');
        return name.substring(0, dot) + " (1)" + name.substring(dot);
    }

    // A chapter's board

    /** Where {@code chapter}'s board was last looked at; from the top-left corner at its own size the first time. */
    public BoardView boardView(String chapter) {

        Properties properties = readProperties(chapterFolder(chapter).resolve(BOARD_FILE));
        return new BoardView(number(properties, "zoom", 1), number(properties, "x", 0), number(properties, "y", 0));
    }

    /** Remembers where {@code chapter}'s board is looked at; only a chapter with notes keeps it. */
    public void saveBoardView(String chapter, BoardView view) {

        Path folder = chapterFolder(chapter);
        if (!Files.isDirectory(folder))
            return;

        Properties properties = new Properties();
        properties.setProperty("zoom", String.valueOf(view.zoom()));
        properties.setProperty("x", String.valueOf(view.x()));
        properties.setProperty("y", String.valueOf(view.y()));
        writeProperties(folder.resolve(BOARD_FILE), properties);
    }

    // Chapters renamed and deleted

    /** Moves {@code from}'s notes to {@code to}, as its chapter was renamed; notes {@code to} already has stay. */
    public void rename(String from, String to) {

        Path source = chapterFolder(from);
        Path target = chapterFolder(to);
        if (!Files.isDirectory(source) || source.equals(target))
            return;

        try {
            if (!Files.exists(target))
                move(source, target);
            else if (Files.isSameFile(source, target)) {
                // The same folder under another letter case: moving it straight there would do nothing.
                Path temporary = source.resolveSibling(source.getFileName() + ".renaming");
                Files.move(source, temporary);
                Files.move(temporary, target);
            } else
                moveNotes(source, target);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot move the notes of " + from + " to " + to, e);
        }
    }

    /**
     * Moves the notes of {@code chapter}, just deleted, to a folder of their own named after it and the time, which
     * frees the name: a chapter that takes it later starts with an empty board.
     */
    public void binChapter(String chapter) {

        Path source = chapterFolder(chapter);
        if (!Files.isDirectory(source))
            return;

        Path bin = binFolder(chapter);
        try {
            if (Files.exists(bin))
                moveNotes(source, bin);
            else
                move(source, bin);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot keep the notes of " + chapter + " in " + bin, e);
        }
        markDeleted(bin, OrphanCause.CHAPTER_DELETED);
    }

    /** Moves the note to a folder named after its chapter and the time, where Deleted notes finds it. */
    public void binNote(String chapter, String id) {

        Path note = noteFolder(chapter, id);
        Path bin = binFolder(chapter);
        try {
            Files.createDirectories(bin);
            move(note, bin.resolve(id));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot move the note " + note + " to " + bin, e);
        }
        if (!Files.exists(bin.resolve(DELETED_FILE)))
            markDeleted(bin, OrphanCause.NOTE_DELETED);

        // A chapter with no notes left keeps no folder.
        try {
            removeIfEmpty(chapterFolder(chapter));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot tidy the notes of " + chapter, e);
        }
    }

    // Orphans

    /**
     * The folders of notes that belong to none of {@code chapters}: deleted in Psiche, or left behind by a chapter
     * renamed or deleted outside it. The most recently deleted come first, then the others by name.
     */
    public List<OrphanGroup> orphans(Collection<String> chapters) {

        return orphanFolders(chapters).stream()
                .map(this::orphan)
                .filter(group -> !group.notes().isEmpty())
                .sorted(Comparator.comparing((OrphanGroup group) -> group.deletedAt().orElse(LocalDateTime.MIN))
                        .reversed()
                        .thenComparing(OrphanGroup::chapter))
                .toList();
    }

    /**
     * True if notes are kept in a folder that belongs to none of {@code chapters}, as {@link #orphans} would list them.
     * Only folders are looked at, not the notes in them, so it is quick enough to ask whenever the chapters change.
     */
    public boolean hasOrphans(Collection<String> chapters) {

        return orphanFolders(chapters).stream().anyMatch(folder -> !subfolders(folder).isEmpty());
    }

    /** The notes of {@code chapter} as it was last deleted, if any are kept; a restored chapter offers them back. */
    public Optional<OrphanGroup> latestDeleted(String chapter) {

        return folders(folder -> folder.getFileName().toString().startsWith(chapter + DELETED_MARK)).stream()
                .map(this::orphan)
                .filter(group -> group.chapter().equals(chapter) && group.cause() == OrphanCause.CHAPTER_DELETED)
                .max(Comparator.comparing(group -> group.deletedAt().orElse(LocalDateTime.MIN)));
    }

    /** Moves the notes of {@code group} to {@code chapter}'s board, and removes the group's folder once it is empty. */
    public void restoreTo(OrphanGroup group, String chapter) {

        try {
            moveNotes(group.folder(), chapterFolder(chapter));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot move the notes of " + group.folder() + " to " + chapter, e);
        }

        // A note the board already had under the same id stays where it was, so nothing is lost.
    }

    /** Deletes the notes of {@code group} and their files for good: they are not in Git, so they cannot come back. */
    public void deletePermanently(OrphanGroup group) {

        deleteTree(group.folder());
    }

    /** The folders in {@code notes/} named after none of {@code chapters}. */
    private List<Path> orphanFolders(Collection<String> chapters) {

        Set<String> names = Set.copyOf(chapters);
        return folders(folder -> !names.contains(folder.getFileName().toString()));
    }

    private OrphanGroup orphan(Path folder) {

        String name = folder.getFileName().toString();
        Matcher deleted = DELETED_FOLDER.matcher(name);
        List<Note> notes = readNotes(folder);
        if (!deleted.matches())
            return new OrphanGroup(folder, name, OrphanCause.CHAPTER_GONE, Optional.empty(), notes);

        Optional<LocalDateTime> at;
        try {
            at = Optional.of(LocalDateTime.parse(deleted.group(2), DELETED_TIME));
        } catch (DateTimeParseException e) {
            return new OrphanGroup(folder, name, OrphanCause.CHAPTER_GONE, Optional.empty(), notes);
        }

        // Folders deleted with a note say so; any other deleted folder went with its chapter.
        String cause = readProperties(folder.resolve(DELETED_FILE)).getProperty("cause", "");
        OrphanCause orphanCause = cause.equals("note") ? OrphanCause.NOTE_DELETED : OrphanCause.CHAPTER_DELETED;
        return new OrphanGroup(folder, deleted.group(1), orphanCause, at, notes);
    }

    private void markDeleted(Path bin, OrphanCause cause) {

        Properties properties = new Properties();
        properties.setProperty("cause", cause == OrphanCause.NOTE_DELETED ? "note" : "chapter");
        writeProperties(bin.resolve(DELETED_FILE), properties);
    }

    private Path binFolder(String chapter) {

        return root.resolve(chapter + DELETED_MARK + LocalDateTime.now(clock).format(DELETED_TIME));
    }

    // Folders and files

    private Path chapterFolder(String chapter) {

        if (chapter.isBlank() || chapter.contains("/") || chapter.contains("\\") || chapter.equals("..")
                || chapter.equals("."))
            throw new IllegalArgumentException("'%s' cannot name a chapter's notes".formatted(chapter));

        return root.resolve(chapter);
    }

    private Path noteFolder(String chapter, String id) {

        Path folder = chapterFolder(chapter).resolve(id);
        if (!Files.isDirectory(folder))
            throw new IllegalArgumentException("No note " + id + " in " + chapter);

        return folder;
    }

    /** The folders directly in {@code notes/} that {@code wanted} accepts; none if there is no {@code notes/}. */
    private List<Path> folders(Predicate<Path> wanted) {

        return subfolders(root).stream().filter(wanted).toList();
    }

    /** Moves each note in {@code source} to {@code target}; one with an id {@code target} already has stays put. */
    private static void moveNotes(Path source, Path target) throws IOException {

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
    private static void removeIfEmpty(Path folder) throws IOException {

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

    /** Reads the properties of the note in {@code folder}, applies {@code change}, and writes them back, alone. */
    void updateProperties(Path folder, Consumer<Properties> change) {

        Path file = folder.resolve(PROPERTIES_FILE);
        synchronized (propertiesLock) {
            Properties properties = readProperties(file);
            change.accept(properties);
            writeProperties(file, properties);
        }
    }
}
