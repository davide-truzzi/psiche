package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.markdown.HeadingAnchors;
import it.infog.psiche.markdown.model.HeadingAnchor;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The headings of every chapter of the book, which the open chapter's links are checked against and the link dialog
 * offers. They are read from the chapters as saved, on a thread of their own, when the book's counts are, so typing
 * never reads the book. Used on the FX thread.
 */
final class BookHeadings {

    private static final Logger log = LoggerFactory.getLogger(BookHeadings.class);

    private final ExecutorService reader =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("book-headings").daemon().factory());
    private final Consumer<Map<String, Set<String>>> read;
    private SequencedMap<String, List<HeadingAnchor>> headings = Collections.emptySortedMap();
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
            SequencedMap<String, List<HeadingAnchor>> found = new LinkedHashMap<>();
            for (Chapter chapter : chapters) {
                try {
                    found.put(chapter.title(), HeadingAnchors.of(book.read(chapter)));
                } catch (UncheckedIOException e) {
                    log.warn("Cannot read the headings of {}", chapter.title(), e);
                    found.put(chapter.title(), List.of());
                }
            }

            Platform.runLater(() -> {
                if (reading != round)
                    return;
                headings = Collections.unmodifiableSequencedMap(found);
                read.accept(anchors(found));
            });
        });
    }

    /** Drops the headings, as no book is open, and a reading still on its way. */
    void clear() {

        round++;
        headings = Collections.emptySortedMap();
        read.accept(null);
    }

    /** Each chapter of the book by name, in the book's order, with its headings as saved; empty until they are read. */
    SequencedMap<String, List<HeadingAnchor>> headings() {

        return headings;
    }

    /** Each chapter of {@code headings} with the anchors of its headings. */
    private static Map<String, Set<String>> anchors(SequencedMap<String, List<HeadingAnchor>> headings) {

        Map<String, Set<String>> anchors = new LinkedHashMap<>();
        headings.forEach((chapter, found) -> {
            Set<String> names = new LinkedHashSet<>();
            found.forEach(heading -> names.add(heading.anchor()));
            anchors.put(chapter, names);
        });

        return anchors;
    }
}
