package it.infog.psiche.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Writing files so that a failure never leaves half of one: a copy goes to a temporary file beside its place, then
 * takes the place in one step. Temporary files start with a dot and end with {@value #TEMPORARY_SUFFIX}, so listings
 * can leave them out.
 */
public final class SafeFiles {

    private static final int COPY_BUFFER = 1 << 20;
    private static final String TEMPORARY_PREFIX = ".";
    private static final String TEMPORARY_SUFFIX = ".tmp";

    private SafeFiles() {
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
     * the file or folder whole where it was; elsewhere, as the file system can.
     */
    public static void move(Path source, Path target, CopyOption... options) throws IOException {

        CopyOption[] atomic = Arrays.copyOf(options, options.length + 1);
        atomic[options.length] = StandardCopyOption.ATOMIC_MOVE;
        try {
            Files.move(source, target, atomic);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, options);
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
}
