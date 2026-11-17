package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.ChapterWatcher;
import it.infog.psiche.ui.chapters.ChapterContext;
import it.infog.psiche.ui.common.StatusBar;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Watches the open book's chapters folder, so chapters added or removed by other programs appear at once. The system
 * may have no watch to spare, as it gives each user only so many: then the author is told, and the watch is tried again
 * every minute until it starts or the book closes. The minutes are counted by a timer of its own, not a JavaFX
 * animation: a running animation draws the window sixty times a second, all the while the watch is missing.
 */
final class ChapterFolderWatch implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ChapterFolderWatch.class);
    private static final long RETRY_MINUTES = 1;

    private final ChapterContext chapters;
    private final StatusBar statusBar;
    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("chapter-watch").daemon().factory());
    // Runs while the chapters folder cannot be watched.
    private ScheduledFuture<?> retries;

    private BookFolder book;
    private ChapterWatcher watcher;
    private String failure;

    ChapterFolderWatch(ChapterContext chapters, StatusBar statusBar) {

        this.chapters = chapters;
        this.statusBar = statusBar;
    }

    /** Watches {@code book}'s chapters folder, in place of any watched before. */
    void start(BookFolder book) {

        close();
        this.book = book;
        if (startWatcher())
            return;

        statusBar.error("Cannot watch the chapters folder for changes by other programs: " + failure
                + ". Trying again every minute");
        retries = timer.scheduleAtFixedRate(() -> Platform.runLater(this::retry), RETRY_MINUTES, RETRY_MINUTES,
                TimeUnit.MINUTES);
    }

    /** True while a book is open but its chapters folder is not watched, so changes there may be missed. */
    boolean isMissingChanges() {

        return book != null && watcher == null;
    }

    /** Stops watching, and trying to. */
    @Override
    public void close() {

        stopRetries();
        if (watcher != null) {
            watcher.close();
            watcher = null;
        }
        book = null;
    }

    /** One more try at watching, while the first has failed; once it works, catches up on what was missed. */
    private void retry() {

        if (book == null || watcher != null || !startWatcher())
            return;

        stopRetries();
        chapters.refresh();
        statusBar.info("Watching the chapters folder again");
    }

    private void stopRetries() {

        if (retries != null)
            retries.cancel(false);
        retries = null;
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
