package it.infog.psiche.book;

import it.infog.psiche.book.model.ProjectManifest;
import it.infog.psiche.common.CopyProgress;
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
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static it.infog.psiche.common.SafeFiles.copy;
import static it.infog.psiche.common.SafeFiles.deleteTree;
import static it.infog.psiche.common.SafeFiles.isTemporary;
import static it.infog.psiche.common.SafeFiles.move;
import static it.infog.psiche.common.SafeFiles.temporaryBeside;

/**
 * A whole book as one Psiche project file, to move it to another computer or keep a copy: a zip with
 * {@value #MANIFEST}, which says what book it is, and the book's folder as it is on disk, under its own name, its Git
 * history and notes included. Text is compressed; every other file is stored as it is, so a book with files of
 * gigabytes exports at the speed of a copy; entries over 4 GB are written as Zip64, by the JDK. Empty folders are kept,
 * as Git may need them.
 * <p>
 * An import unpacks the book into a hidden folder beside its place, and only once it is whole does the book take its
 * place; a folder it replaces is removed after that, and comes back if the book cannot take its place. A file whose
 * entry would leave the book's folder is refused, and so is a file written by a later Psiche in a format this one does
 * not know.
 */
public final class ProjectArchive {

    /** The extension of a Psiche project file, without its dot. */
    public static final String EXTENSION = "psiche";

    static final String MANIFEST = "psiche-project.properties";

    private static final Logger log = LoggerFactory.getLogger(ProjectArchive.class);
    // The manifest's format; a file made by a later Psiche, in a format this one does not know, is refused.
    private static final int FORMAT = 1;
    private static final List<String> TEXT_EXTENSIONS = List.of(".md", ".properties", ".txt", ".css", ".gitignore");
    private static final int COPY_BUFFER = 1 << 20;
    // Hidden folders beside the book's place, where an import unpacks it and where a folder it replaces waits.
    private static final String IMPORTING = ".import-";
    private static final String REPLACED = ".replaced-";

    private ProjectArchive() {
    }

    /**
     * Writes everything in the folder {@code book} to {@code target}, as {@code manifest} describes it; Psiche's own
     * temporary files, and {@code target} itself if it is in the folder, are left out. {@code progress} hears how many
     * bytes are written, of how many. The file is written beside its place and takes it once whole.
     */
    public static void export(Path book, Path target, ProjectManifest manifest, CopyProgress progress) {

        try {
            List<Path> contents = contents(book, target);
            long total = contents.stream().filter(Files::isRegularFile).mapToLong(ProjectArchive::size).sum();
            Path temporary = temporaryBeside(target);
            try {
                try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
                        Files.newOutputStream(temporary)), StandardCharsets.UTF_8)) {
                    putManifest(out, manifest);
                    long written = 0;
                    for (Path path : contents)
                        written += put(out, book, manifest.folder(), path, written, total, progress);
                }
                move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot export the book to " + target + ": " + e.getMessage(), e);
        }
    }

    /**
     * What the project file {@code archive} says about the book in it.
     *
     * @throws UncheckedIOException if it cannot be read, or is no Psiche project this Psiche can import
     */
    public static ProjectManifest manifest(Path archive) {

        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            return manifest(zip, archive);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + archive + ": " + e.getMessage(), e);
        }
    }

    /**
     * Unpacks the book in {@code archive} into the folder {@code parent}, under the folder name it was exported with,
     * in place of a folder of that name if there is one. {@code progress} hears how many bytes are unpacked, of how
     * many.
     *
     * @return the book's folder
     * @throws UncheckedIOException if the file cannot be read or holds a file outside the book's folder, or the book
     *                              cannot be written; any folder it was to replace is then as it was
     */
    public static Path importTo(Path archive, Path parent, CopyProgress progress) {

        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            ProjectManifest manifest = manifest(zip, archive);
            Path target = parent.resolve(manifest.folder());
            Path work = Files.createTempDirectory(parent, "." + manifest.folder() + IMPORTING);
            try {
                unpack(zip, manifest.folder(), work, progress);
                place(work, target);
            } finally {
                deleteTree(work);
            }
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot import " + archive + ": " + e.getMessage(), e);
        }
    }

    // Export

    /** The folders and files in {@code book}, in order, but links, Psiche's temporary files and {@code target}. */
    private static List<Path> contents(Path book, Path target) throws IOException {

        Path left = target.toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(book)) {
            return paths.filter(path -> !path.equals(book))
                    .filter(path -> !path.toAbsolutePath().normalize().equals(left))
                    .filter(path -> !isTemporary(path.getFileName().toString()))
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                            || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .toList();
        }
    }

    private static void putManifest(ZipOutputStream out, ProjectManifest manifest) throws IOException {

        Properties properties = new Properties();
        properties.setProperty("format", String.valueOf(FORMAT));
        properties.setProperty("folder", manifest.folder());
        properties.setProperty("title", manifest.title());
        properties.setProperty("book", manifest.book());
        properties.setProperty("exported", manifest.exported().toString());

        StringWriter text = new StringWriter();
        properties.store(text, null);
        out.setLevel(Deflater.DEFAULT_COMPRESSION);
        out.putNextEntry(new ZipEntry(MANIFEST));
        out.write(text.toString().getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    /**
     * Writes {@code path}, a folder or a file of {@code book}, under {@code folder}, compressed if it is text, and
     * tells {@code progress} how far the export has come.
     *
     * @return the bytes written; none for a folder, or a file deleted while the export ran
     */
    private static long put(ZipOutputStream out, Path book, String folder, Path path, long before, long total,
                            CopyProgress progress) throws IOException {

        String relative = book.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
        String name = folder + "/" + relative;
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            out.putNextEntry(new ZipEntry(name + "/"));
            out.closeEntry();
            return 0;
        }

        boolean text = TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
        out.setLevel(text ? Deflater.DEFAULT_COMPRESSION : Deflater.NO_COMPRESSION);
        long copied = 0;
        try (InputStream in = Files.newInputStream(path)) {
            out.putNextEntry(new ZipEntry(name));
            byte[] buffer = new byte[COPY_BUFFER];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                out.write(buffer, 0, read);
                copied += read;
                progress.copied(before + copied, total);
            }
            out.closeEntry();
        } catch (NoSuchFileException e) {
            log.warn("Not exporting {}: it was deleted meanwhile", path);
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

    private static ProjectManifest manifest(ZipFile zip, Path archive) throws IOException {

        ZipEntry entry = zip.getEntry(MANIFEST);
        if (entry == null)
            throw new IOException(archive.getFileName() + " is no Psiche project");

        Properties properties = new Properties();
        try (InputStream in = zip.getInputStream(entry)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        int format;
        try {
            format = Integer.parseInt(properties.getProperty("format", ""));
        } catch (NumberFormatException e) {
            throw new IOException(archive.getFileName() + " is a damaged Psiche project", e);
        }
        if (format > FORMAT)
            throw new IOException(archive.getFileName()
                    + " was exported by a later Psiche Writer: update it to import");

        String folder = properties.getProperty("folder", "");
        if (!isFolderName(folder) || format < 1)
            throw new IOException(archive.getFileName() + " is a damaged Psiche project");

        LocalDateTime exported;
        try {
            exported = LocalDateTime.parse(properties.getProperty("exported", ""));
        } catch (DateTimeParseException e) {
            throw new IOException(archive.getFileName() + " is a damaged Psiche project", e);
        }

        return new ProjectManifest(folder, properties.getProperty("title", folder), properties.getProperty("book", ""),
                exported);
    }

    /** Writes every folder and file under {@code folder} in {@code zip} into {@code work}. */
    private static void unpack(ZipFile zip, String folder, Path work, CopyProgress progress) throws IOException {

        List<? extends ZipEntry> entries = zip.stream().filter(entry -> !entry.getName().equals(MANIFEST)).toList();
        long total = entries.stream().mapToLong(entry -> Math.max(0, entry.getSize())).sum();
        long done = 0;
        for (ZipEntry entry : entries) {
            Path path = inside(work, folder, entry.getName());
            if (entry.isDirectory()) {
                Files.createDirectories(path);
                continue;
            }

            Files.createDirectories(path.getParent());
            long before = done;
            try (InputStream in = zip.getInputStream(entry)) {
                copy(in, entry.getSize(), path, (copied, size) -> progress.copied(before + copied, total));
            }
            done += Math.max(0, entry.getSize());
        }
    }

    /**
     * Where the entry {@code name} goes in {@code work}, the book's folder being unpacked.
     *
     * @throws IOException if it is not under {@code folder}, or would be written outside it
     */
    private static Path inside(Path work, String folder, String name) throws IOException {

        if (!name.startsWith(folder + "/"))
            throw new IOException("It holds " + name + ", which is not in the book's folder");

        String path = name.substring(folder.length() + 1);
        for (String part : path.split("/"))
            if (part.equals("..") || part.equals(".") || part.contains("\\"))
                throw new IOException("It holds " + name + ", which would be written outside the book's folder");

        Path resolved;
        try {
            resolved = work.resolve(path).normalize();
        } catch (InvalidPathException e) {
            throw new IOException("It holds " + name + ", which cannot be a file here", e);
        }
        if (!resolved.startsWith(work))
            throw new IOException("It holds " + name + ", which would be written outside the book's folder");

        return resolved;
    }

    /**
     * Gives {@code work} the place of {@code target}. A folder there is moved aside first, and removed once the book is
     * in its place; if the book cannot take it, the folder is put back.
     */
    private static void place(Path work, Path target) throws IOException {

        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            move(work, target);
            return;
        }

        Path old = Files.createTempDirectory(target.getParent(), "." + target.getFileName() + REPLACED);
        Files.delete(old);
        move(target, old);
        try {
            move(work, target);
        } catch (IOException e) {
            move(old, target);
            throw e;
        }
        deleteTree(old);
    }

    /** True if {@code name} can name a book's folder: one plain name, not hidden. */
    private static boolean isFolderName(String name) {

        return !name.isBlank() && !name.startsWith(".") && !name.contains("/") && !name.contains("\\");
    }
}
