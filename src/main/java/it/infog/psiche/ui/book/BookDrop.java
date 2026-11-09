package it.infog.psiche.ui.book;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/**
 * Opens a book dropped on the window while none is open, on the welcome: its folder, or a folder or file inside it,
 * the first if several are dropped. While a book is open the window takes no drop of its own, so the notes keep
 * theirs.
 */
public final class BookDrop {

    private BookDrop() {}

    /**
     * Lets {@code target}, the window's content, take a book dropped while no book is open and the Book menu could
     * open one.
     *
     * @param ready false while the Book menu could not open a book, as during a Git command or a project transfer
     */
    public static void install(Node target, BookContext books, BooleanSupplier ready) {

        target.addEventFilter(DragEvent.DRAG_OVER, event -> {
            if (!takes(event.getDragboard(), books, ready))
                return;

            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });

        target.addEventFilter(DragEvent.DRAG_DROPPED, event -> {
            if (!takes(event.getDragboard(), books, ready))
                return;

            Path dropped = event.getDragboard().getFiles().getFirst().toPath();
            event.setDropCompleted(true);
            event.consume();
            // Once the drop has ended, so a dialog it may lead to does not keep the file manager waiting.
            Platform.runLater(() -> books.openDropped(dropped));
        });
    }

    private static boolean takes(Dragboard dragboard, BookContext books, BooleanSupplier ready) {

        return !books.bookOpenProperty().get() && ready.getAsBoolean() && dragboard.hasFiles()
                && !dragboard.getFiles().isEmpty();
    }
}
