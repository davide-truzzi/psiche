package it.infog.psiche.settings.model;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The books opened lately, the latest first: each once, however it was reached, and at most {@value #MAX}.
 *
 * @param books the books' folders, the latest first
 */
public record RecentBooks(List<Path> books) {

    public static final int MAX = 10;

    public RecentBooks {

        books = List.copyOf(books);
    }

    /** No book opened yet. */
    public static RecentBooks none() {

        return new RecentBooks(List.of());
    }

    /** The list once {@code book} has been opened: first, out of its earlier place, the oldest dropped past MAX. */
    public RecentBooks opened(Path book) {

        Path opened = identity(book);
        List<Path> books = new ArrayList<>();
        books.add(opened);
        this.books.stream()
                .filter(earlier -> !identity(earlier).equals(opened))
                .limit(MAX - 1)
                .forEach(books::add);

        return new RecentBooks(books);
    }

    /** The list without {@code book}, however it was reached, the others kept in their order. */
    public RecentBooks without(Path book) {

        Path removed = identity(book);
        return new RecentBooks(books.stream().filter(recent -> !identity(recent).equals(removed)).toList());
    }

    /**
     * The path a book is known by, so that one reached through a link, or in another letter case where the file
     * system ignores it, is still the same book: its real path if it exists, its absolute path otherwise.
     */
    static Path identity(Path book) {

        try {
            return book.toRealPath();
        } catch (IOException e) {
            return book.toAbsolutePath().normalize();
        }
    }
}
