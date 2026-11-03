package it.infog.psiche.ui.chapters;

import it.infog.psiche.spelling.model.Word;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.WindowEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static it.infog.psiche.ui.common.Controls.menuItem;

/**
 * The spelling part of the manuscript's right-click menu, over a word marked as a mistake: up to five words the author
 * may have meant, the likeliest first, each replacing it as one change Undo reverts; then Add to dictionary and Ignore,
 * which both make it right in every book, for now; then the rest of the menu. Elsewhere, the menu is as it was.
 */
final class SpellingMenu {

    private static final String NO_SUGGESTIONS = "No suggestions";

    private SpellingMenu() {
    }

    /**
     * Adds the spelling items to {@code menu}, the manuscript's, each time it opens over a mistake.
     *
     * @param ownWord makes a word right in every book: the author's word list takes it, and the text is checked again
     */
    static void attach(ContextMenu menu, Manuscript manuscript, Consumer<String> ownWord) {

        List<MenuItem> spellingItems = new ArrayList<>();
        menu.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> {
            menu.getItems().removeAll(spellingItems);
            spellingItems.clear();
            manuscript.menuMistake().ifPresent(mistake -> {
                spellingItems.addAll(items(manuscript, mistake, ownWord));
                menu.getItems().addAll(0, spellingItems);
            });
        });
    }

    /** The items for {@code mistake}: its suggestions, or a line saying there are none, then the author's choices. */
    private static List<MenuItem> items(Manuscript manuscript, Word mistake, Consumer<String> ownWord) {

        List<MenuItem> items = new ArrayList<>();
        for (String suggestion : manuscript.suggestions(mistake.text())) {
            MenuItem item = menuItem(suggestion, () -> manuscript.replace(mistake.range().start(),
                    mistake.range().end(), suggestion));
            // A suggestion is a word, never a label with a shortcut letter in it.
            item.setMnemonicParsing(false);
            items.add(item);
        }
        if (items.isEmpty())
            items.add(menuItem(NO_SUGGESTIONS, () -> {}, false));

        items.add(new SeparatorMenuItem());
        items.add(menuItem("Add to dictionary", () -> ownWord.accept(mistake.text())));
        items.add(menuItem("Ignore", () -> ownWord.accept(mistake.text())));
        items.add(new SeparatorMenuItem());
        return items;
    }
}
