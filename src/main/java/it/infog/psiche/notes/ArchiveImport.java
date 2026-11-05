package it.infog.psiche.notes;

import it.infog.psiche.common.CopyProgress;
import it.infog.psiche.notes.model.ImportReport;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NoteLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static it.infog.psiche.common.SafeFiles.copy;
import static it.infog.psiche.common.SafeFiles.deleteTree;
import static it.infog.psiche.common.SafeFiles.move;
import static it.infog.psiche.notes.NoteFolders.DUPLICATE_KEY;
import static it.infog.psiche.notes.NoteFolders.FILE_KEY;
import static it.infog.psiche.notes.NoteFolders.PROPERTIES_FILE;
import static it.infog.psiche.notes.NoteFolders.TEXT_FILE;
import static it.infog.psiche.notes.NoteFolders.attachmentName;
import static it.infog.psiche.notes.NoteFolders.layout;
import static it.infog.psiche.notes.NoteFolders.loadProperties;
import static it.infog.psiche.notes.NoteFolders.putLayout;
import static it.infog.psiche.notes.NoteFolders.readNote;
import static it.infog.psiche.notes.NoteFolders.readNotes;
import static it.infog.psiche.notes.NoteFolders.readProperties;
import static it.infog.psiche.notes.NoteFolders.subfolders;
import static it.infog.psiche.notes.NoteFolders.writeAtomically;
import static it.infog.psiche.notes.NoteFolders.writeProperties;

/**
 * One import of an export of notes into the book's, merging by note id. Each note keeps the fingerprints of its
 * content and of its layout at its last export or import here, which tell which side changed it since:
 * <ul>
 *     <li>a note this computer lacks is added, in the folder the zip has it in;</li>
 *     <li>content changed only in the zip is taken; changed only here, kept; changed on both, the zip's is added
 *     beside it as a copy tagged Duplicate on Import;</li>
 *     <li>a place, size or colour changed only in the zip is taken; changed on both, this computer's is kept;</li>
 *     <li>a note keeps the folder it has here, so a note deleted on either side stays as it is here.</li>
 * </ul>
 * Every path in the zip is checked before anything changes; one that would leave {@code notes/} refuses the zip. A
 * note is written in a folder whose name starts with a dot, which the notes never list, and moved into place once
 * whole.
 */
final class ArchiveImport {

    // A duplicate sits this far beside the note it duplicates.
    private static final double BESIDE_GAP = 16;
    private static final String WORK_FOLDER = ".import-";

    private static final Logger log = LoggerFactory.getLogger(ArchiveImport.class);

    private final NoteStore store;
    private final Path root;
    private final ZipFile zip;
    private final CopyProgress progress;
    private final long total;
    private long done;
    private int added;
    private int updated;
    private int duplicated;

    /** A note in the zip: the folder it is in, its id, and its files by name. */
    private record ZipNote(String folder,
                           String id,
                           Map<String, ZipEntry> files) {
    }

    /** What a note in the zip holds, read from its entries. */
    private record Theirs(String text,
                          Properties properties,
                          Optional<String> file,
                          long fileSize) {
    }

    ArchiveImport(NoteStore store, ZipFile zip, CopyProgress progress) {

        this.store = store;
        this.root = store.root();
        this.zip = zip;
        this.progress = progress;
        this.total = Collections.list(zip.entries()).stream().mapToLong(entry -> Math.max(0, entry.getSize())).sum();
    }

    ImportReport run() throws IOException {

        //read and check every entry, before anything changes
        Map<String, ZipNote> notes = new TreeMap<>();
        Map<String, ZipEntry> loose = new TreeMap<>();
        for (ZipEntry entry : Collections.list(zip.entries())) {
            if (entry.isDirectory() || entry.getName().equals(NotesArchive.MANIFEST))
                continue;

            List<String> parts = parts(entry.getName());
            switch (parts.size()) {
                case 2 -> loose.put(parts.get(0) + "/" + parts.get(1), entry);
                case 3 -> notes.computeIfAbsent(parts.get(0) + "/" + parts.get(1),
                                key -> new ZipNote(parts.get(0), parts.get(1), new TreeMap<>()))
                        .files().put(parts.get(2), entry);
                default -> log.warn("Not importing {}: it is no file of a note", entry.getName());
            }
        }

        //merge
        Map<String, Path> here = notesHere();
        for (ZipNote note : notes.values()) {
            Path mine = here.get(note.id());
            if (mine == null)
                here.put(note.id(), add(note));
            else
                merge(note, mine);
            done += note.files().values().stream().mapToLong(ZipEntry::getSize).sum();
            progress.copied(done, total);
        }

        //the boards' views and the marks of deleted folders, where this computer has none
        for (Map.Entry<String, ZipEntry> file : loose.entrySet()) {
            Path target = root.resolve(file.getKey());
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                extract(file.getValue(), target);
            }
        }

        return new ImportReport(added, updated, duplicated);
    }

    /**
     * The parts of {@code name} under {@code notes/}: none for a file Psiche never reads, such as a temporary one.
     *
     * @throws IOException if {@code name} is outside {@code notes/}, or would leave it
     */
    private List<String> parts(String name) throws IOException {

        if (!name.startsWith(NotesArchive.NOTES_FOLDER))
            throw new IOException("The zip holds " + name + ", which is not a note");

        List<String> parts = List.of(name.substring(NotesArchive.NOTES_FOLDER.length()).split("/", -1));
        for (String part : parts)
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.contains("\\")
                    || !root.resolve(part).normalize().getParent().equals(root.normalize()))
                throw new IOException("The zip holds " + name + ", which would be written outside the notes");

        return parts.stream().anyMatch(part -> part.startsWith(".")) ? List.of() : parts;
    }

    /** Every note here, by id, in whichever folder it is; the first found if an id is in two. */
    private Map<String, Path> notesHere() {

        Map<String, Path> notes = new HashMap<>();
        for (Path folder : subfolders(root))
            for (Path note : subfolders(folder))
                notes.putIfAbsent(note.getFileName().toString(), note);

        return notes;
    }

    // A note this computer lacks

    /** Writes {@code note} in its folder, with the fingerprints of what it holds. @return where it is now */
    private Path add(ZipNote note) throws IOException {

        Path folder = root.resolve(note.folder());
        Path target = folder.resolve(note.id());
        Files.createDirectories(folder);
        Path work = Files.createTempDirectory(folder, WORK_FOLDER);
        try {
            extractAll(note, work);
            Path properties = work.resolve(PROPERTIES_FILE);
            Properties written = readProperties(properties);
            Note read = readNote(work).orElseThrow(() -> new IOException("Cannot read the note " + note.id()));
            written.setProperty(NotePrints.CONTENT_KEY, NotePrints.content(read));
            written.setProperty(NotePrints.LAYOUT_KEY, NotePrints.layout(read.layout()));
            writeProperties(properties, written);
            move(work, target);
        } finally {
            deleteTree(work);
        }

        added++;
        return target;
    }

    // A note both sides have

    private void merge(ZipNote note, Path mine) throws IOException {

        Note current = readNote(mine).orElse(null);
        if (current == null) {
            log.warn("Not merging the note in {}: it cannot be read here", mine);
            return;
        }

        //what changed, and where
        Theirs theirs = read(note);
        Properties base = readProperties(mine.resolve(PROPERTIES_FILE));
        String baseContent = base.getProperty(NotePrints.CONTENT_KEY, "");
        String baseLayout = base.getProperty(NotePrints.LAYOUT_KEY, "");
        String myContent = NotePrints.content(current);
        String theirContent = NotePrints.content(theirs.text(), theirs.file(), theirs.fileSize());
        String myLayout = NotePrints.layout(current.layout());
        NoteLayout theirLayout = layout(theirs.properties());
        String theirLayoutPrint = NotePrints.layout(theirLayout);

        boolean contentChangedThere = !theirContent.equals(myContent) && !theirContent.equals(baseContent);
        boolean layoutChangedThere = !theirLayoutPrint.equals(myLayout) && !theirLayoutPrint.equals(baseLayout);

        //layout: theirs if only they changed it; a note's place in the stack stays this computer's
        NoteLayout layout = current.layout();
        boolean layoutTaken = layoutChangedThere && myLayout.equals(baseLayout);
        if (layoutTaken)
            layout = new NoteLayout(theirLayout.x(), theirLayout.y(), theirLayout.width(), theirLayout.height(),
                    layout.order(), theirLayout.color());

        //content: theirs if only they changed it; both versions if both did
        boolean contentTaken = contentChangedThere && myContent.equals(baseContent);
        if (contentTaken)
            takeContent(note, mine, current, theirs);
        else if (contentChangedThere)
            addDuplicate(note, mine, layout, theirContent);

        // From now on the zip's versions are what this computer last imported.
        NoteLayout kept = layout;
        Optional<String> file = contentTaken ? theirs.file() : Optional.empty();
        store.updateProperties(mine, properties -> {
            putLayout(properties, kept);
            if (contentTaken)
                file.ifPresentOrElse(name -> properties.setProperty(FILE_KEY, name),
                        () -> properties.remove(FILE_KEY));
            properties.setProperty(NotePrints.CONTENT_KEY, theirContent);
            properties.setProperty(NotePrints.LAYOUT_KEY, theirLayoutPrint);
        });

        if (contentChangedThere && !contentTaken)
            duplicated++;
        else if (contentTaken || layoutTaken)
            updated++;
    }

    /** Gives the note in {@code mine} the text and file of {@code theirs}; the file it had goes, kept in the backup. */
    private void takeContent(ZipNote note, Path mine, Note current, Theirs theirs) throws IOException {

        writeAtomically(mine.resolve(TEXT_FILE), theirs.text());

        Optional<ZipEntry> file = theirs.file().map(note.files()::get);
        if (file.isPresent())
            extract(file.get(), mine.resolve(theirs.file().get()));

        Optional<Path> old = current.attachment();
        if (old.isPresent() && !old.get().getFileName().toString().equals(theirs.file().orElse("")))
            Files.deleteIfExists(old.get());
    }

    /**
     * Adds the zip's version of a note beside the one in {@code mine}, which keeps its own: on the same board, over
     * the other notes, tagged Duplicate on Import, with a new id.
     */
    private void addDuplicate(ZipNote note, Path mine, NoteLayout beside, String content) throws IOException {

        Path folder = mine.getParent();
        Path work = Files.createTempDirectory(folder, WORK_FOLDER);
        try {
            extractAll(note, work);
            Path properties = work.resolve(PROPERTIES_FILE);
            Properties written = readProperties(properties);
            int top = readNotes(folder).stream().mapToInt(other -> other.layout().order()).max().orElse(0) + 1;
            NoteLayout placed = besides(beside, layout(written), top);

            putLayout(written, placed);
            written.setProperty(DUPLICATE_KEY, "true");
            written.setProperty(NotePrints.CONTENT_KEY, content);
            written.setProperty(NotePrints.LAYOUT_KEY, NotePrints.layout(placed));
            writeProperties(properties, written);

            // The new id's folder is made empty, so no other note takes it, then the duplicate takes its place.
            move(work, store.newNoteFolder(folder), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            deleteTree(work);
        }
    }

    /** {@code theirs} moved to the right of {@code mine}, or to its left near the board's edge, at {@code order}. */
    private static NoteLayout besides(NoteLayout mine, NoteLayout theirs, int order) {

        double x = mine.x() + mine.width() + BESIDE_GAP;
        if (x + theirs.width() > NoteLayout.BOARD_WIDTH)
            x = mine.x() - theirs.width() - BESIDE_GAP;

        return new NoteLayout(Math.clamp(x, 0, NoteLayout.BOARD_WIDTH - theirs.width()),
                Math.clamp(mine.y(), 0, NoteLayout.BOARD_HEIGHT - theirs.height()), theirs.width(), theirs.height(),
                order, theirs.color());
    }

    // The zip's entries

    private Theirs read(ZipNote note) throws IOException {

        ZipEntry textEntry = note.files().get(TEXT_FILE);
        String text = "";
        if (textEntry != null)
            try (InputStream in = zip.getInputStream(textEntry)) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

        Properties properties = new Properties();
        ZipEntry propertiesEntry = note.files().get(PROPERTIES_FILE);
        if (propertiesEntry != null)
            try (InputStream in = zip.getInputStream(propertiesEntry)) {
                properties = loadProperties(new InputStreamReader(in, StandardCharsets.UTF_8),
                        propertiesEntry.getName());
            }

        Optional<String> file = attachmentName(properties, note.files().keySet().stream());
        long size = file.map(note.files()::get).map(ZipEntry::getSize).orElse(-1L);
        return new Theirs(text, properties, file, size);
    }

    private void extractAll(ZipNote note, Path folder) throws IOException {

        for (Map.Entry<String, ZipEntry> file : note.files().entrySet())
            extract(file.getValue(), folder.resolve(file.getKey()));
    }

    /** Writes {@code entry} to {@code target}, whole or not at all, telling how far the import has come. */
    private void extract(ZipEntry entry, Path target) throws IOException {

        try (InputStream in = zip.getInputStream(entry)) {
            copy(in, entry.getSize(), target, (copied, size) -> progress.copied(done + copied, total));
        }
    }
}
