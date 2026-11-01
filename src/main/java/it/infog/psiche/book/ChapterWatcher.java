package it.infog.psiche.book;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * Notifies when chapter files appear in or disappear from a book's {@code chapters/} directory, e.g. after a
 * {@code git pull} or a file manager rename. The callback runs on the watcher thread.
 */
public final class ChapterWatcher implements AutoCloseable {

    private final WatchService watchService;

    public ChapterWatcher(BookFolder book, Runnable onChaptersChanged) {

        WatchService service = null;
        try {
            service = book.chaptersDir().getFileSystem().newWatchService();
            book.chaptersDir().register(service, ENTRY_CREATE, ENTRY_DELETE);
        } catch (IOException e) {
            // A watch service is a scarce resource of the system's: give back one that could not be used.
            closeQuietly(service);
            throw new UncheckedIOException("Cannot watch " + book.chaptersDir(), e);
        }
        watchService = service;

        Thread.ofVirtual().name("chapter-watcher").start(() -> watch(onChaptersChanged));
    }

    private void watch(Runnable onChaptersChanged) {

        try {
            WatchKey key;
            do {
                key = watchService.take();
                if (key.pollEvents().stream().anyMatch(ChapterWatcher::affectsChapters))
                    onChaptersChanged.run();
            } while (key.reset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException e) {
            // close() was called; nothing left to watch.
        }
    }

    private static void closeQuietly(WatchService service) {

        if (service == null)
            return;

        try {
            service.close();
        } catch (IOException e) {
            // Nothing more can be done with it.
        }
    }

    private static boolean affectsChapters(WatchEvent<?> event) {

        return event.kind() == OVERFLOW
                || event.context() instanceof Path file && file.toString().endsWith(BookFolder.CHAPTER_EXTENSION);
    }

    @Override
    public void close() {

        try {
            watchService.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
