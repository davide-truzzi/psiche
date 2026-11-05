package it.infog.psiche.notes;

import it.infog.psiche.notes.model.ImportReport;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NotesManifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static it.infog.psiche.common.SafeFiles.move;
import static it.infog.psiche.common.SafeFiles.temporaryBeside;
import static it.infog.psiche.notes.NoteFolders.loadProperties;
import static it.infog.psiche.notes.NoteFolders.readNote;
import static it.infog.psiche.notes.NoteFolders.subfolders;

/**
 * A book's notes as a zip, to move them to another computer or keep a copy: {@value #MANIFEST} saying whose notes they
 * are, and {@code notes/} as it is on disk, Detached notes included. Text is compressed; every other file is stored as
 * it is, so an export of multi-GB files runs at the speed of a copy. Entries over 4 GB are written as Zip64, by the
 * JDK.
 * <p>
 * An export remembers each note's fingerprints, so a later import can tell which side changed it; a backup, made before
 * an import, leaves them as they are. An import merges, as {@link ArchiveImport} tells.
 */
public final class NotesArchive {

    static final String MANIFEST = "manifest.properties";
    static final String NOTES_FOLDER = "notes/";

    private static final Logger log = LoggerFactory.getLogger(NotesArchive.class);
    // The manifest's format; an export made by a later Psiche, in a format this one does not know, is refused.
    private static final int FORMAT = 1;
    private static final List<String> TEXT_EXTENSIONS = List.of(".md", ".properties", ".txt");

    private final NoteStore store;

    public NotesArchive(NoteStore store) {

        this.store = store;
    }

    /**
     * Reads what the export in {@code zip} says about itself.
     *
     * @throws UncheckedIOException if {@code zip} cannot be read, or is no export of notes this Psiche can import
     */
    public static NotesManifest manifest(Path zip) {

        try (ZipFile file = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            return manifest(file, zip);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + zip + ": " + e.getMessage(), e);
        }
    }

    /**
     * Writes every note of the book to {@code zip}, and remembers each note's fingerprints as exported.
     * {@code progress} hears how many bytes are written, of how many.
     */
    public void export(Path zip, NotesManifest manifest, NoteStore.CopyProgress progress) {

        notes().forEach(this::rememberPrints);
        write(zip, manifest, progress);
    }

    /** Writes every note of the book to {@code zip} as {@link #export} does, but leaves their fingerprints alone. */
    public void backup(Path zip, NotesManifest manifest, NoteStore.CopyProgress progress) {

        write(zip, manifest, progress);
    }

    /**
     * Merges the notes exported in {@code zip} into the book's: notes it lacks are added, notes changed only in the zip
     * are updated, notes changed on both sides are kept twice. Nothing is changed if the zip is no export of notes, or
     * holds a path that would leave {@code notes/}.
     *
     * @throws UncheckedIOException if {@code zip} cannot be read or the notes written; the notes merged until then stay
     */
    public ImportReport importFrom(Path zip, NoteStore.CopyProgress progress) {

        try (ZipFile file = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            manifest(file, zip);
            return new ArchiveImport(store, file, progress).run();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot import the notes in " + zip + ": " + e.getMessage(), e);
        }
    }

    // Export

    private void write(Path zip, NotesManifest manifest, NoteStore.CopyProgress progress) {

        Path root = store.root();
        List<Path> files = files(root);
        long total = files.stream().mapToLong(NotesArchive::size).sum();

        try {
            Path temporary = temporaryBeside(zip);
            try {
                try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
                        Files.newOutputStream(temporary)), StandardCharsets.UTF_8)) {
                    putManifest(out, manifest);
                    long written = 0;
                    for (Path file : files)
                        written += put(out, root, file, written, total, progress);
                }
                move(temporary, zip, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the notes to " + zip, e);
        }
    }

    /** Every note of the book, in every folder of {@code notes/}, Detached notes included. */
    private Stream<Path> notes() {

        return subfolders(store.root()).stream().flatMap(folder -> subfolders(folder).stream());
    }

    /** Keeps in the note in {@code folder} the fingerprints of what it is now, as the export holds it. */
    private void rememberPrints(Path folder) {

        Note note = readNote(folder).orElse(null);
        if (note == null)
            return;

        try {
            String content = NotePrints.content(note);
            String layout = NotePrints.layout(note.layout());
            store.updateProperties(folder, properties -> {
                properties.setProperty(NotePrints.CONTENT_KEY, content);
                properties.setProperty(NotePrints.LAYOUT_KEY, layout);
            });
        } catch (UncheckedIOException e) {
            // Deleted from the board while the export ran: it is not exported either.
            log.warn("Not remembering the note in {}: it cannot be written", folder, e);
        }
    }

    /**
     * The files under {@code root}, but temporary ones and those in a folder whose name starts with a dot, which
     * Psiche never reads as notes.
     */
    private static List<Path> files(Path root) {

        if (!Files.isDirectory(root))
            return List.of();

        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> StreamSupport.stream(root.relativize(path).spliterator(), false)
                            .noneMatch(part -> part.toString().startsWith(".")))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the notes in " + root, e);
        }
    }

    private static void putManifest(ZipOutputStream out, NotesManifest manifest) throws IOException {

        Properties properties = new Properties();
        properties.setProperty("format", String.valueOf(FORMAT));
        properties.setProperty("book", manifest.book());
        properties.setProperty("title", manifest.title());

        StringWriter text = new StringWriter();
        properties.store(text, null);
        out.setLevel(Deflater.DEFAULT_COMPRESSION);
        out.putNextEntry(new ZipEntry(MANIFEST));
        out.write(text.toString().getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    /**
     * Writes {@code file}, compressed if it is text, and tells {@code progress} how far the export has come.
     *
     * @return the bytes written; none for a file deleted while the export ran
     */
    private static long put(ZipOutputStream out, Path root, Path file, long before, long total,
                            NoteStore.CopyProgress progress) throws IOException {

        String name = NOTES_FOLDER + root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
        boolean text = TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
        out.setLevel(text ? Deflater.DEFAULT_COMPRESSION : Deflater.NO_COMPRESSION);

        long copied = 0;
        try (InputStream in = Files.newInputStream(file)) {
            out.putNextEntry(new ZipEntry(name));
            byte[] buffer = new byte[1 << 20];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                out.write(buffer, 0, read);
                copied += read;
                progress.copied(before + copied, total);
            }
            out.closeEntry();
        } catch (NoSuchFileException e) {
            log.warn("Not exporting {}: it was deleted meanwhile", file);
        }
        return copied;
    }

    private static long size(Path file) {

        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    // Import

    private static NotesManifest manifest(ZipFile file, Path zip) throws IOException {

        ZipEntry entry = file.getEntry(MANIFEST);
        if (entry == null)
            throw new IOException(zip.getFileName() + " is no export of Psiche notes");

        Properties properties;
        try (InputStream in = file.getInputStream(entry)) {
            properties = loadProperties(new InputStreamReader(in, StandardCharsets.UTF_8), zip + "!" + MANIFEST);
        }

        int format = (int) NoteFolders.number(properties, "format", 0);
        if (format < 1 || format > FORMAT)
            throw new IOException(zip.getFileName() + " was exported by a later Psiche Writer: update it to import");

        return new NotesManifest(properties.getProperty("book", ""), properties.getProperty("title", ""));
    }
}
