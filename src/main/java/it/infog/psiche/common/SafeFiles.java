package it.infog.psiche.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writing files so that a failure never leaves half of one: a copy goes to a temporary file beside its place, then
 * takes the place in one step. Temporary files start with a dot and end with {@value #TEMPORARY_SUFFIX}, so listings
 * can leave them out.
 */
public final class SafeFiles {

    private static final Logger log = LoggerFactory.getLogger(SafeFiles.class);
    private static final int COPY_BUFFER = 1 << 20;
    private static final String TEMPORARY_PREFIX = ".";
    private static final String TEMPORARY_SUFFIX = ".tmp";
    // How long to wait before each new try of a step another program held up, in milliseconds: a few tenths of a
    // second in all, the time a virus scanner takes over a small file.
    private static final List<Long> RETRY_PAUSES = List.of(20L, 50L, 100L, 200L);

    private SafeFiles() {
    }

    /** A step on the disk, which may fail. */
    @FunctionalInterface
    public interface DiskStep<T> {

        T run() throws IOException;
    }

    /**
     * Runs {@code step}, and again after a pause each time another program may have held it up: on Windows, a virus
     * scanner, the search indexer or OneDrive often opens a file just written, and while they have it, it cannot be
     * read or replaced. Any other failure, and the last one, goes to the caller; a step that works after waiting is
     * logged, as it tells why saving is slow on some computers.
     */
    public static <T> T patiently(DiskStep<T> step) throws IOException {

        IOException heldUp = null;
        for (int tried = 0; ; tried++) {
            try {
                T result = step.run();
                if (heldUp != null)
                    log.info("Done at try {}, after another program held up {}", tried + 1, heldUp.getMessage());
                return result;
            } catch (IOException e) {
                if (!mayPass(e) || tried == RETRY_PAUSES.size())
                    throw e;
                heldUp = e;
            }

            try {
                Thread.sleep(RETRY_PAUSES.get(tried));
            } catch (InterruptedException e) {
                // A thread asked to stop does not wait.
                Thread.currentThread().interrupt();
                throw heldUp;
            }
        }
    }

    /** True if {@code name} is the name of a temporary file, as {@link #temporaryBeside} makes them. */
    public static boolean isTemporary(String name) {

        return name.startsWith(TEMPORARY_PREFIX) && name.endsWith(TEMPORARY_SUFFIX);
    }

    /** A new temporary file beside {@code file}, named after it. */
    public static Path temporaryBeside(Path file) throws IOException {

        return Files.createTempFile(file.getParent(), TEMPORARY_PREFIX + file.getFileName(), TEMPORARY_SUFFIX);
    }

    /**
     * Moves {@code source} to {@code target} in one step where the file system allows it, so a move that fails leaves
     * the file or folder whole where it was; elsewhere, as the file system can. A move another program holds up is
     * tried again, as {@link #patiently} does.
     */
    public static void move(Path source, Path target, CopyOption... options) throws IOException {

        CopyOption[] atomic = Arrays.copyOf(options, options.length + 1);
        atomic[options.length] = StandardCopyOption.ATOMIC_MOVE;
        try {
            patiently(() -> Files.move(source, target, atomic));
        } catch (AtomicMoveNotSupportedException e) {
            patiently(() -> Files.move(source, target, options));
        }
    }

    /**
     * Moves {@code source} to {@code target}, the same file or folder under a name that differs only in letter case,
     * on a file system that ignores it: moved straight there, it would keep its name.
     */
    public static void changeCase(Path source, Path target) throws IOException {

        Path temporary = source.resolveSibling(source.getFileName() + ".renaming");
        Files.move(source, temporary);
        Files.move(temporary, target);
    }

    /** Writes {@code text} to a temporary file beside {@code file}, which then takes its place whole. */
    public static void writeString(Path file, String text) throws IOException {

        Path temporary = temporaryBeside(file);
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            // Gone once moved; left only by a write that failed.
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Copies {@code in}, of {@code total} bytes, to a temporary file beside {@code target}, which then takes its place;
     * {@code progress} hears how far the copy has come.
     */
    public static void copy(InputStream in, long total, Path target, CopyProgress progress) throws IOException {

        Path temporary = temporaryBeside(target);
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[COPY_BUFFER];
                long copied = 0;
                for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                    out.write(buffer, 0, read);
                    copied += read;
                    progress.copied(copied, total);
                }
            }
            move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Deletes {@code folder} and everything in it; nothing if it does not exist. */
    public static void deleteTree(Path folder) {

        if (!Files.exists(folder))
            return;

        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + folder, e);
        }
    }

    /**
     * True if {@code error} may be another program holding the file for a moment: Windows reports it as access denied,
     * or as a sharing violation, which Java gives as a plain {@link FileSystemException}. Told by the error's type,
     * not its message, which is in the system's language.
     */
    private static boolean mayPass(IOException error) {

        return error instanceof AccessDeniedException || error.getClass() == FileSystemException.class;
    }
}
