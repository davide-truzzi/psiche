package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.markdown.HeadingAnchors;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The anchors of the headings of every chapter of the book, which the open chapter's links are checked against. They
 * are read from the chapters as saved, on a thread of their own, when the book's counts are, so typing never reads the
 * book. Used on the FX thread.
 */
final class BookHeadings {

    private static final Logger log = LoggerFactory.getLogger(BookHeadings.class);

    private final ExecutorService reader =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("book-headings").daemon().factory());
    private final Consumer<Map<String, Set<String>>> read;
    // Reading again starts a new round; a slower reading of an earlier round is dropped when it comes back.
    private int round;

    /**
     * @param read given, on the FX thread, each chapter's name with the anchors of its headings, every time they are
     *             read; {@code null} once they are dropped, as while no book is open
     */
    BookHeadings(Consumer<Map<String, Set<String>>> read) {

        this.read = read;
    }

    /**
     * Reads the headings of {@code chapters} of {@code book} again, as saved; a chapter that cannot be read is listed
     * without headings, so links to it still find it.
     */
    void read(BookFolder book, List<Chapter> chapters) {

        int reading = ++round;
        reader.execute(() -> {
            Map<String, Set<String>> anchors = new HashMap<>();
            for (Chapter chapter : chapters) {
                try {
                    anchors.put(chapter.title(), HeadingAnchors.anchors(book.read(chapter)));
                } catch (UncheckedIOException e) {
                    log.warn("Cannot read the headings of {}", chapter.title(), e);
                    anchors.put(chapter.title(), Set.of());
                }
            }

            Map<String, Set<String>> readAnchors = Map.copyOf(anchors);
            Platform.runLater(() -> {
                if (reading == round)
                    read.accept(readAnchors);
            });
        });
    }

    /** Drops the headings, as no book is open, and a reading still on its way. */
    void clear() {

        round++;
        read.accept(null);
    }
}
