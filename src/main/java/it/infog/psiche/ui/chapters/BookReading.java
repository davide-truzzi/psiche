package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.markdown.HeadingAnchors;
import it.infog.psiche.markdown.TextCounter;
import it.infog.psiche.markdown.model.HeadingAnchor;
import it.infog.psiche.markdown.model.TextCount;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Reads the book's chapters as saved, each once, for all that needs the whole book: its counts, under the chapter
 * list, and the headings the links are checked against. They are read on a thread of their own, when the book opens,
 * when a chapter is saved, and when one is added, removed or renamed, so typing never reads the book. Used on the FX
 * thread.
 */
final class BookReading {

    private static final Logger log = LoggerFactory.getLogger(BookReading.class);

    private final ExecutorService reader =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("book-reading").daemon().factory());
    private final Consumer<TextCount> counted;
    private final Consumer<SequencedMap<String, List<HeadingAnchor>>> headed;
    // Reading again starts a new round; a slower reading of an earlier round is dropped when it comes back.
    private int round;

    /**
     * @param counted given, on the FX thread, the characters and words of the whole book, each time it is read
     * @param headed  given, on the FX thread, each chapter's name, in the book's order, with its headings
     */
    BookReading(Consumer<TextCount> counted, Consumer<SequencedMap<String, List<HeadingAnchor>>> headed) {

        this.counted = counted;
        this.headed = headed;
    }

    /**
     * Reads {@code chapters} of {@code book} again, as saved. A chapter that cannot be read is left out of the counts,
     * and listed without headings, so links to it still find it.
     */
    void read(BookFolder book, List<Chapter> chapters) {

        int reading = ++round;
        List<Chapter> toRead = List.copyOf(chapters);
        reader.execute(() -> {
            int characters = 0;
            int words = 0;
            SequencedMap<String, List<HeadingAnchor>> headings = new LinkedHashMap<>();
            for (Chapter chapter : toRead) {
                try {
                    String text = book.read(chapter);
                    TextCount count = TextCounter.count(text);
                    characters += count.characters();
                    words += count.words();
                    headings.put(chapter.title(), HeadingAnchors.of(text));
                } catch (UncheckedIOException e) {
                    log.warn("Cannot read {}", chapter.title(), e);
                    headings.put(chapter.title(), List.of());
                }
            }

            TextCount total = new TextCount(characters, words);
            Platform.runLater(() -> {
                if (reading != round)
                    return;
                counted.accept(total);
                headed.accept(Collections.unmodifiableSequencedMap(headings));
            });
        });
    }

    /** Drops a reading still on its way, as the book closes. */
    void clear() {

        round++;
    }
}
