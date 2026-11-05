package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.settings.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Where the author's books are: the books opened lately, and where the folder choosers start, beside the open book or
 * the latest one, or else in the home folder, rather than wherever the system starts, which may be the folder Psiche
 * is installed in. A book is known by its folder as the file system knows it, so one reached through a link matches.
 */
final class BookLocations {

    private final Settings settings;

    /** @param settings where the books opened lately are remembered */
    BookLocations(Settings settings) {

        this.settings = settings;
    }

    /**
     * The books opened lately, the latest first, {@code open} left out if a book is open. A book whose folder is gone
     * stays listed, as it may be on a drive that is not plugged in.
     */
    List<Path> recentBooks(BookFolder open) {

        Optional<Path> openFolder = Optional.ofNullable(open).map(book -> realPath(book.root()));
        return settings.recentBooks().books().stream()
                .filter(recent -> openFolder.isEmpty() || !realPath(recent).equals(openFolder.get()))
                .toList();
    }

    /** True if {@code folder} is the folder of {@code open}, the open book, if any. */
    static boolean isFolderOf(BookFolder open, Path folder) {

        return open != null && realPath(open.root()).equals(realPath(folder));
    }

    /**
     * Where to start choosing a book folder: the folder holding {@code open}, the open book if any, or else the latest
     * book still there, where the author most likely keeps other books; the home folder if there is none.
     */
    Path booksDirectory(BookFolder open) {

        if (open != null)
            return open.root().getParent();

        // With no book open, start next to the latest book that is still there.
        return settings.recentBooks().books().stream()
                .filter(Files::isDirectory)
                .findFirst()
                .map(Path::getParent)
                .orElseGet(BookLocations::homeDirectory);
    }

    /**
     * Where to start choosing where a new book goes: where the other books are, if Psiche may write there, or else the
     * home folder.
     */
    Path newBooksDirectory(BookFolder open) {

        return Optional.ofNullable(booksDirectory(open))
                .filter(Files::isWritable)
                .orElseGet(BookLocations::homeDirectory);
    }

    /** The author's home folder; null, to let the dialog choose, if the system does not say where it is. */
    private static Path homeDirectory() {

        return Optional.ofNullable(System.getProperty("user.home"))
                .map(Path::of)
                .filter(Files::isDirectory)
                .orElse(null);
    }

    /** {@code path} as the file system knows it, so a book reached through a link matches; as given if it is gone. */
    private static Path realPath(Path path) {

        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }
}
