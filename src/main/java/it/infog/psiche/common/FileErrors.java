package it.infog.psiche.common;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Why a file or folder could not be written, as a plain sentence for the author instead of the system's terse one,
 * which for a missing permission names only the path. Where the place is not the author's to write in, the sentence
 * suggests one that is.
 */
public final class FileErrors {

    // What the systems say, in English, when a drive is read-only or full; in another language, their own words show.
    private static final List<String> READ_ONLY = List.of("read-only file system", "write protected");
    private static final List<String> FULL = List.of("no space left", "not enough space", "disk quota exceeded");
    private static final String OWN_FOLDER = "choose a folder of your own, such as Documents or your home folder.";

    private FileErrors() {
    }

    /** Why {@code error} happened, as a sentence ending with a full stop. */
    public static String reason(IOException error) {

        String message = Optional.ofNullable(error instanceof FileSystemException system ? system.getReason() : null)
                .or(() -> Optional.ofNullable(error.getMessage()))
                .orElse("")
                .toLowerCase(Locale.ROOT);
        if (READ_ONLY.stream().anyMatch(message::contains))
            return "The drive is read-only: " + OWN_FOLDER;
        if (FULL.stream().anyMatch(message::contains))
            return "The disk is full: free some space, or choose another drive.";

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
}
