package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.ui.common.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;

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
 * book, shows greyed out as "(not found on location: '<folder>')": it may be on a drive that is not plugged in, or
 * have been moved. A cross beside it takes it off the list, the other books staying.
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
            items.add(entry.found() ? menuItem(label, () -> books.openRecent(entry.folder())) : notFound(entry, label));
        }
        items.add(new SeparatorMenuItem());
        items.add(menuItem("Clear list", books::clearRecentBooks));

        menu.getItems().setAll(items);
        menu.setDisable(busy || entries.isEmpty());
    }

    /**
     * A book that is not found, greyed out, with a cross that takes it off the list and out of the menu, which stays
     * open for the next one. The item itself is not disabled, as that would disable the cross too.
     */
    private MenuItem notFound(Entry entry, String label) {

        Label text = new Label("%s (not found on location: '%s')".formatted(label, entry.folder().getParent()));
        text.getStyleClass().add("recent-book-missing");

        SVGPath cross = Icons.shape("remove");
        cross.getStyleClass().add("recent-book-remove-icon");
        Button remove = new Button();
        remove.setGraphic(cross);
        remove.getStyleClass().add("recent-book-remove");
        remove.setTooltip(new Tooltip("Remove from the list"));

        HBox row = new HBox(text, remove);
        row.getStyleClass().add("recent-book-missing-row");
        row.setAlignment(Pos.CENTER_LEFT);

        // Hidden rather than removed: a menu that is showing does not redraw for an item taken out of its list. The
        // next refresh leaves it out.
        CustomMenuItem item = new CustomMenuItem(row, false);
        remove.setOnAction(event -> {
            books.forgetRecentBook(entry.folder());
            item.setVisible(false);
        });

        return item;
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
