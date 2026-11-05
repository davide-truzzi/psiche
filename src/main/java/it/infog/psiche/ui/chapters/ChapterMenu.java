package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.ui.export.Exports;
import it.infog.psiche.ui.git.GitControls;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static it.infog.psiche.ui.common.Controls.menuItem;

/**
 * A chapter's right-click menu in the chapter list: Save, Rename, Delete and Export. Save writes the chapter's unsaved
 * changes, whether it is open or not. Rename and Delete make a commit of their own, so they need the book's repository
 * and wait while another Git command runs; Save and Export need neither.
 */
final class ChapterMenu {

    private final ChapterEditor chapterEditor;
    private final Exports exports;
    private final GitControls gitControls;
    private final ObservableBooleanValue hasRepository;
    private final Supplier<BookFolder> book;
    private final Consumer<Chapter> rename;
    private final Consumer<Chapter> delete;

    /**
     * @param hasRepository true while the open book has a Git repository
     * @param book          the open book
     * @param rename        renames a chapter, asking for its new title
     * @param delete        deletes a chapter, asking first
     */
    ChapterMenu(ChapterEditor chapterEditor, Exports exports, GitControls gitControls,
                ObservableBooleanValue hasRepository, Supplier<BookFolder> book, Consumer<Chapter> rename,
                Consumer<Chapter> delete) {

        this.chapterEditor = chapterEditor;
        this.exports = exports;
        this.gitControls = gitControls;
        this.hasRepository = hasRepository;
        this.book = book;
        this.rename = rename;
        this.delete = delete;
    }

    /** The menu of the chapter in {@code cell}, whichever it holds when the menu opens. */
    ContextMenu of(ListCell<Chapter> cell) {

        MenuItem saveItem = menuItem("Save", () -> chapterEditor.save(cell.getItem()));
        MenuItem renameItem = menuItem("Rename", () -> rename.accept(cell.getItem()));
        MenuItem deleteItem = menuItem("Delete", () -> delete.accept(cell.getItem()));
        MenuItem needsGit = new MenuItem("Needs a Git repository");
        needsGit.setDisable(true);

        Menu export = new Menu("Export");
        for (ExportFormat format : Exports.CHAPTER_FORMATS)
            export.getItems().add(menuItem(format.label(),
                    () -> exports.exportChapter(book.get(), cell.getItem(), format)));

        ContextMenu menu = new ContextMenu(saveItem, new SeparatorMenuItem(), renameItem, deleteItem, needsGit,
                new SeparatorMenuItem(), export);
        menu.setOnShowing(event -> {
            saveItem.setDisable(!chapterEditor.unsavedChapters().contains(cell.getItem()));
            renameItem.setDisable(!hasRepository.get() || gitControls.busyProperty().get());
            deleteItem.setDisable(renameItem.isDisable());
            needsGit.setVisible(!hasRepository.get());
        });

        return menu;
    }
}
