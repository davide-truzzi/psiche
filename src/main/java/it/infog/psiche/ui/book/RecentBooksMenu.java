package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static it.infog.psiche.ui.common.Controls.menuItem;

/**
 * Open recent, in the Book menu: the books opened lately, by their titles, and Clear list. It is filled again each
 * time the Book menu opens, so it lists what the settings hold then. A book whose folder is gone, or no longer holds a
 * book, shows greyed out as "(not found)": it may be on a drive that is not plugged in.
 */
public final class RecentBooksMenu {

    private final Menu menu = new Menu("Open recent");
    private final BookContext books;

    public RecentBooksMenu(BookContext books) {

        this.books = books;
    }

    public Menu menu() {

        return menu;
    }

    /** Lists the books opened lately; with none, the menu is disabled, as it is while {@code busy}. */
    public void refresh(boolean busy) {

        List<Entry> entries = books.recentBooks().stream().map(RecentBooksMenu::entry).toList();
        // Two books of the same title are told apart by the folder they are in.
        Map<String, Long> titles = entries.stream().collect(Collectors.groupingBy(Entry::title, Collectors.counting()));

        List<MenuItem> items = new ArrayList<>();
        for (Entry entry : entries) {
            String label = titles.get(entry.title()) > 1
                    ? "%s — %s".formatted(entry.title(), entry.folder().getParent().getFileName())
                    : entry.title();
            MenuItem item = menuItem(entry.found() ? label : label + " (not found)",
                    () -> books.openRecent(entry.folder()));
            item.setDisable(!entry.found());
            items.add(item);
        }
        items.add(new SeparatorMenuItem());
        items.add(menuItem("Clear list", books::clearRecentBooks));

        menu.getItems().setAll(items);
        menu.setDisable(busy || entries.isEmpty());
    }

    /** A recent book: its title from its properties, or its folder's name if it cannot be opened or read. */
    private static Entry entry(Path folder) {

        Optional<BookFolder> book;
        try {
            book = Optional.of(BookFolder.open(folder));
        } catch (IllegalArgumentException | UncheckedIOException e) {
            book = Optional.empty();
        }

        String title = book.map(RecentBooksMenu::title).orElse(folder.getFileName().toString());
        return new Entry(folder, title, book.isPresent());
    }

    private static String title(BookFolder book) {

        try {
            return book.properties().title();
        } catch (UncheckedIOException e) {
            return book.title();
        }
    }

    /**
     * @param found false if the folder is gone or holds no book
     */
    private record Entry(Path folder,
                         String title,
                         boolean found) {
    }
}
