package it.infog.psiche.ui.book;

import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.ui.chapters.ChapterEditor;
import it.infog.psiche.ui.export.Exports;
import it.infog.psiche.ui.git.GitControls;
import it.infog.psiche.ui.notes.NotesTransfer;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import static it.infog.psiche.ui.common.Controls.menuItem;

/**
 * Book, in the toolbar, which opens a menu of what acts on the whole book: create or open one, save every chapter,
 * edit its properties, export it, export or import its notes or the whole project. Each chapter's own actions, Save
 * among them, are in its right-click menu. An item waits while what it needs is busy: Git, the notes on their way in
 * or out, or a Psiche project being exported or imported, which keeps the whole menu shut.
 */
public final class BookMenu {

    private BookMenu() {
    }

    /** The Book button, which opens the menu under it. */
    public static Button button(BookContext books, ChapterEditor editor, GitControls git, NotesTransfer notesTransfer,
                                ProjectTransfer projectTransfer) {

        ObservableBooleanValue gitBusy = git.busyProperty();
        ObservableBooleanValue noBook = books.bookOpenProperty().not();

        MenuItem create = menuItem("New book", books::createBook);
        create.disableProperty().bind(gitBusy);

        MenuItem open = menuItem("Open book", books::chooseBook);
        open.disableProperty().bind(gitBusy);

        RecentBooksMenu recent = new RecentBooksMenu(books);

        // The shortcut is shown here and bound by WriterApp; a menu opened from a button does not bind it.
        MenuItem saveAll = menuItem("Save chapters", editor::saveAll);
        saveAll.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN));
        saveAll.disableProperty().bind(Bindings.isEmpty(editor.unsavedChapters()));

        // A Git command, or notes on their way in or out, still need the book.
        MenuItem close = menuItem("Close book", books::closeOpenBook);
        close.disableProperty().bind(Bindings.or(noBook, gitBusy).or(notesTransfer.busyProperty()));

        MenuItem properties = menuItem("Book properties", books::editProperties);
        properties.disableProperty().bind(Bindings.or(noBook, gitBusy));

        // Notes are not in Git: these move them to another computer, or keep a copy.
        MenuItem exportNotes = menuItem("Export notes", books::exportNotes);
        exportNotes.disableProperty().bind(Bindings.or(noBook, notesTransfer.busyProperty()));

        MenuItem importNotes = menuItem("Import notes", books::importNotes);
        importNotes.disableProperty().bind(exportNotes.disableProperty());

        // The whole book folder, notes and history included, to one file.
        MenuItem exportProject = menuItem("Export Psiche project", books::exportProject);
        exportProject.disableProperty().bind(exportNotes.disableProperty().or(gitBusy));

        // It may replace the open book, which then closes: as Close book, it waits for Git and the notes.
        MenuItem importProject = menuItem("Import Psiche project", books::importProject);
        importProject.disableProperty().bind(Bindings.or(gitBusy, notesTransfer.busyProperty()));

        ContextMenu menu = new ContextMenu(create, open, recent.menu(), close, new SeparatorMenuItem(), saveAll,
                new SeparatorMenuItem(), properties, exportMenu(books, gitBusy, noBook), new SeparatorMenuItem(),
                exportNotes, importNotes, new SeparatorMenuItem(), exportProject, importProject);
        menu.setOnShowing(event -> recent.refresh(gitBusy.get()));

        Button book = new Button("Book");
        book.setOnAction(event -> menu.show(book, Side.BOTTOM, 0, 4));
        // Nothing in it may change the book while a Psiche project is exported or imported.
        book.disableProperty().bind(projectTransfer.busyProperty());

        return book;
    }

    /** Export: the book in each format, then its writing history and the book at a past revision. */
    private static Menu exportMenu(BookContext books, ObservableBooleanValue gitBusy, ObservableBooleanValue noBook) {

        Menu export = new Menu("Export");
        for (ExportFormat format : Exports.BOOK_FORMATS)
            export.getItems().add(menuItem(format.label(), () -> books.exportBook(format)));

        // Read on the Git thread, after the commands clicked before.
        MenuItem writingHistory = menuItem("Writing history (PDF)", books::exportWritingHistory);
        writingHistory.disableProperty().bind(gitBusy);
        MenuItem revision = menuItem("Book at a revision…", books::exportRevision);
        revision.disableProperty().bind(gitBusy);

        export.getItems().addAll(new SeparatorMenuItem(), writingHistory, revision);
        export.disableProperty().bind(noBook);

        return export;
    }
}
