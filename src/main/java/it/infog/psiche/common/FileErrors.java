package it.infog.psiche.common;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Why a file or folder could not be written, as a plain sentence for the author instead of the system's terse one,
 * which for a missing permission names only the path. Where the place is not the author's to write in, the sentence
 * suggests one that is.
 * <p>
 * A read-only or full drive is told by asking the drive itself, so in any language of the system; the system's own
 * words, in English, are only a fallback for drives that cannot say (a disk quota, some network shares).
 */
public final class FileErrors {

    // What the systems say, in English, when a drive is read-only or full.
    private static final List<String> READ_ONLY = List.of("read-only file system", "write protected");
    private static final List<String> FULL = List.of("no space left", "not enough space", "disk quota exceeded");
    // A drive with less room than this is taken as full: Psiche writes small files, a book's own are a few kilobytes.
    private static final long FULL_BELOW = 1024 * 1024;
    private static final String OWN_FOLDER = "choose a folder of your own, such as Documents or your home folder.";
    private static final String DRIVE_READ_ONLY = "The drive is read-only: " + OWN_FOLDER;
    private static final String DISK_FULL = "The disk is full: free some space, or choose another drive.";

    private FileErrors() {
    }

    /**
     * Why {@code error} happened while writing at {@code place}, as a sentence ending with a full stop.
     * {@code place} is the file or folder being written; its drive is asked when the error names no file.
     */
    public static String reason(IOException error, Path place) {

        return reason(error, store(error, place));
    }

    /** As {@link #reason(IOException, Path)}, with the drive already found, or none when it cannot be asked. */
    static String reason(IOException error, Optional<FileStore> store) {

        if (store.filter(FileStore::isReadOnly).isPresent())
            return DRIVE_READ_ONLY;
        if (store.filter(FileErrors::isFull).isPresent())
            return DISK_FULL;

        String message = Optional.ofNullable(error instanceof FileSystemException system ? system.getReason() : null)
                .or(() -> Optional.ofNullable(error.getMessage()))
                .orElse("")
                .toLowerCase(Locale.ROOT);
        if (READ_ONLY.stream().anyMatch(message::contains))
            return DRIVE_READ_ONLY;
        if (FULL.stream().anyMatch(message::contains))
            return DISK_FULL;

        return switch (error) {
            case AccessDeniedException denied -> "Psiche Writer has no permission to write there: " + OWN_FOLDER;
            case FileAlreadyExistsException exists -> "There is already a file or folder of that name.";
            case NoSuchFileException gone -> "The folder it goes in is not there any more.";
            case NotDirectoryException file -> "A file stands where a folder should be.";
            default -> Optional.ofNullable(error.getMessage())
                    .map(text -> "The system says: " + text + (text.endsWith(".") ? "" : "."))
                    .orElse("The system gave no reason (" + error.getClass().getSimpleName() + ").");
        };
    }

    /**
     * The drive holding the file {@code error} names, else {@code place}, or the nearest of its folders that exists;
     * none when it cannot be asked.
     */
    private static Optional<FileStore> store(IOException error, Path place) {

        try {
            Path written = error instanceof FileSystemException system && system.getFile() != null
                    ? Path.of(system.getFile())
                    : place;
            Optional<Path> existing = Stream.iterate(written.toAbsolutePath(), path -> path != null, Path::getParent)
                    .filter(Files::exists)
                    .findFirst();
            return existing.isEmpty() ? Optional.empty() : Optional.of(Files.getFileStore(existing.get()));
        } catch (IOException | InvalidPathException | SecurityException e) {
            return Optional.empty();
        }
    }

    private static boolean isFull(FileStore store) {

        try {
            return store.getUsableSpace() < FULL_BELOW;
        } catch (IOException e) {
            return false;
        }
    }
}
