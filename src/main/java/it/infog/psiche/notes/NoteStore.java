package it.infog.psiche.notes;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.common.CopyProgress;
import it.infog.psiche.common.PropertiesFiles;
import it.infog.psiche.notes.model.BoardView;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NoteLayout;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.function.Consumer;

import static it.infog.psiche.common.SafeFiles.copy;
import static it.infog.psiche.common.SafeFiles.move;
import static it.infog.psiche.notes.NoteFolders.BOARD_FILE;
import static it.infog.psiche.notes.NoteFolders.DUPLICATE_KEY;
import static it.infog.psiche.notes.NoteFolders.FILE_KEY;
import static it.infog.psiche.notes.NoteFolders.OWN_FILES;
import static it.infog.psiche.notes.NoteFolders.PROPERTIES_FILE;
import static it.infog.psiche.notes.NoteFolders.TEXT_FILE;
import static it.infog.psiche.notes.NoteFolders.moveNotes;
import static it.infog.psiche.notes.NoteFolders.number;
import static it.infog.psiche.notes.NoteFolders.putLayout;
import static it.infog.psiche.notes.NoteFolders.readNote;
import static it.infog.psiche.notes.NoteFolders.readNotes;
import static it.infog.psiche.notes.NoteFolders.removeIfEmpty;
import static it.infog.psiche.notes.NoteFolders.writeAtomically;

/**
 * A book's chapter notes, kept in {@code notes/} at the book's root, outside Git. Each chapter with notes has a folder
 * named like the chapter's file, without {@code .md}, holding one folder per note, named by the note's id: in it,
 * {@code note.md} holds the text, {@code note.properties} the note's place, size, colour and tag and the name
 * of the file it carries, and the file itself sits beside them. The chapter's folder also keeps
 * {@code board.properties}, where its board was last looked at.
 * <p>
 * The whole book has a board of its own, kept the same way in the folder {@value #BOOK}, a name no chapter may take:
 * every method that takes a chapter takes {@link #BOOK} for it.
 * <p>
 * Nothing here is destroyed. A deleted note, or the notes of a deleted chapter, go to the {@link #detached()} notes,
 * and so does a file a note no longer carries, replaced or removed, with a copy of the note; only there can they be
 * deleted for good. How each note is kept on disk is {@link NoteFolders}'.
 */
public final class NoteStore {

    /** The book's own board, given where a chapter is asked for. */
    public static final String BOOK = BookFolder.BOOK_NOTES;

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    // Where a board was last looked at, in its properties.
    private static final String ZOOM_KEY = "zoom";
    private static final String X_KEY = "x";
    private static final String Y_KEY = "y";

    private final Path root;
    private final Clock clock;
    private final DetachedNoteStore detached;
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
        detached = new DetachedNoteStore(root, clock);
    }

    /** The book's {@code notes/} folder, which may not exist yet. */
    Path root() {

        return root;
    }

    /** The notes off their boards, deleted or left without a chapter, which Detached notes shows. */
    public DetachedNoteStore detached() {

        return detached;
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
     * Detached notes, with a copy of the note. The copy is written beside its place and moved there once whole, so an
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

    /** Takes the note's file away, to Detached notes with a copy of the note; the note keeps its text. */
    public void removeAttachment(String chapter, String id) {

        binFile(chapter, id);
        updateProperties(noteFolder(chapter, id), properties -> properties.remove(FILE_KEY));
    }

    /** Moves the note's file, if it has one on disk, to a copy of the note in Detached notes. */
    private void binFile(String chapter, String id) {

        Path folder = noteFolder(chapter, id);
        Optional<Path> file = readNote(folder).flatMap(Note::attachment).filter(Files::exists);
        if (file.isEmpty())
            return;

        Path bin = detached.deletedFolder(chapter);
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
        detached.markNoteDeleted(bin);
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

        Properties properties = PropertiesFiles.readLeniently(chapterFolder(chapter).resolve(BOARD_FILE));
        return new BoardView(number(properties, ZOOM_KEY, 1), number(properties, X_KEY, 0),
                number(properties, Y_KEY, 0));
    }

    /** Remembers where {@code chapter}'s board is looked at; only a chapter with notes keeps it. */
    public void saveBoardView(String chapter, BoardView view) {

        Path folder = chapterFolder(chapter);
        if (!Files.isDirectory(folder))
            return;

        Properties properties = new Properties();
        properties.setProperty(ZOOM_KEY, String.valueOf(view.zoom()));
        properties.setProperty(X_KEY, String.valueOf(view.x()));
        properties.setProperty(Y_KEY, String.valueOf(view.y()));
        PropertiesFiles.write(folder.resolve(BOARD_FILE), properties, "");
    }

    /**
     * Moves the note, with its file, from {@code from}'s board to {@code to}'s, in the same place, over the notes
     * there; a chapter with no notes left keeps no folder. A note already there under its id keeps it, and this one
     * takes a new one.
     */
    public void moveNote(String from, String id, String to) {

        Path note = noteFolder(from, id);
        Path target = chapterFolder(to).resolve(id);
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                target = newNoteFolder(chapterFolder(to));
                Files.delete(target);
            }
            int top = readNotes(chapterFolder(to)).stream().mapToInt(each -> each.layout().order()).max().orElse(0);
            move(note, target);
            updateProperties(target, properties -> {
                NoteLayout layout = NoteFolders.layout(properties);
                putLayout(properties, new NoteLayout(layout.x(), layout.y(), layout.width(), layout.height(), top + 1,
                        layout.color()));
            });
            removeIfEmpty(chapterFolder(from));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot move the note " + note + " to " + to, e);
        }
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

        Path bin = detached.deletedFolder(chapter);
        try {
            if (Files.exists(bin))
                moveNotes(source, bin);
            else
                move(source, bin);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot keep the notes of " + chapter + " in " + bin, e);
        }
        detached.markChapterDeleted(bin);
    }

    /** Moves the note to a folder named after its chapter and the time, where Detached notes finds it. */
    public void binNote(String chapter, String id) {

        Path note = noteFolder(chapter, id);
        Path bin = detached.deletedFolder(chapter);
        try {
            Files.createDirectories(bin);
            move(note, bin.resolve(id));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot move the note " + note + " to " + bin, e);
        }
        detached.markNoteDeleted(bin);

        // A chapter with no notes left keeps no folder.
        try {
            removeIfEmpty(chapterFolder(chapter));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot tidy the notes of " + chapter, e);
        }
    }

    // Folders and files

    private Path chapterFolder(String chapter) {

        return NoteFolders.chapterFolder(root, chapter);
    }

    private Path noteFolder(String chapter, String id) {

        Path folder = chapterFolder(chapter).resolve(id);
        if (!Files.isDirectory(folder))
            throw new IllegalArgumentException("No note " + id + " in " + chapter);

        return folder;
    }

    /** Reads the properties of the note in {@code folder}, applies {@code change}, and writes them back, alone. */
    void updateProperties(Path folder, Consumer<Properties> change) {

        Path file = folder.resolve(PROPERTIES_FILE);
        synchronized (propertiesLock) {
            Properties properties = PropertiesFiles.readLeniently(file);
            change.accept(properties);
            PropertiesFiles.write(file, properties, "");
        }
    }
}
