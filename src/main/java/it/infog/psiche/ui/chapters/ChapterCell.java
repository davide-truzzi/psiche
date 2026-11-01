package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.Chapter;
import javafx.collections.ObservableSet;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.function.Function;

/** A chapter in the chapter list: its title, marked "unsaved" while it has unsaved changes, and its menu. */
final class ChapterCell extends ListCell<Chapter> {

    private final Label unsavedMark = new Label("unsaved");
    private final ObservableSet<Chapter> unsaved;
    private final ContextMenu menu;

    /**
     * @param unsaved the chapters with unsaved changes; the list redraws its cells when the set changes
     * @param menu    makes the menu of a cell, for the chapter it shows when the menu opens
     */
    ChapterCell(ObservableSet<Chapter> unsaved, Function<ListCell<Chapter>, ContextMenu> menu) {

        this.unsaved = unsaved;
        this.menu = menu.apply(this);

        unsavedMark.getStyleClass().add("unsaved-mark");
        setContentDisplay(ContentDisplay.RIGHT);
        setGraphicTextGap(10);

        // Right-clicking a chapter shows its menu without selecting it, which would open it.
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY)
                event.consume();
        });
    }

    @Override
    protected void updateItem(Chapter chapter, boolean empty) {

        super.updateItem(chapter, empty);
        setText(empty ? null : chapter.title());
        setGraphic(!empty && unsaved.contains(chapter) ? unsavedMark : null);
        setContextMenu(empty ? null : menu);
    }
}
