package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.ChapterWatcher;
import it.infog.psiche.ui.chapters.ChapterContext;
import it.infog.psiche.ui.common.StatusBar;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Watches the open book's chapters folder, so chapters added or removed by other programs appear at once. The system
 * may have no watch to spare, as it gives each user only so many: then the author is told, and the watch is tried again
 * every minute until it starts or the book closes.
 */
final class ChapterFolderWatch implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ChapterFolderWatch.class);
    private static final Duration RETRY = Duration.minutes(1);

    private final ChapterContext chapters;
    private final StatusBar statusBar;
    // Runs while the chapters folder cannot be watched.
    private final Timeline retries = new Timeline(new KeyFrame(RETRY, event -> retry()));

    private BookFolder book;
    private ChapterWatcher watcher;
    private String failure;

    ChapterFolderWatch(ChapterContext chapters, StatusBar statusBar) {

        this.chapters = chapters;
        this.statusBar = statusBar;
        retries.setCycleCount(Animation.INDEFINITE);
    }

    /** Watches {@code book}'s chapters folder, in place of any watched before. */
    void start(BookFolder book) {

        close();
        this.book = book;
        if (startWatcher())
            return;

        statusBar.error("Cannot watch the chapters folder for changes by other programs: " + failure
                + ". Trying again every minute");
        retries.playFromStart();
    }

    /** True while a book is open but its chapters folder is not watched, so changes there may be missed. */
    boolean isMissingChanges() {

        return book != null && watcher == null;
    }

    /** Stops watching, and trying to. */
    @Override
    public void close() {

        retries.stop();
        if (watcher != null) {
            watcher.close();
            watcher = null;
        }
        book = null;
    }

    /** One more try at watching, while the first has failed; once it works, catches up on what was missed. */
    private void retry() {

        if (book == null || !startWatcher())
            return;

        retries.stop();
        chapters.refresh();
        statusBar.info("Watching the chapters folder again");
    }

    private boolean startWatcher() {

        try {
            watcher = new ChapterWatcher(book, () -> Platform.runLater(chapters::refresh));
            return true;
        } catch (UncheckedIOException e) {
            log.warn("Cannot watch the chapters of {}", book.root(), e);
            failure = Optional.ofNullable(e.getCause().getMessage()).orElse("the system refused");
            return false;
        }
    }
}
