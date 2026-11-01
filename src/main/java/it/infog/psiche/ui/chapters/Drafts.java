package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Text of chapters that differs from what is on disk, kept in memory until it is saved or discarded. */
public final class Drafts {

    private final Map<Chapter, String> texts = new HashMap<>();
    private final ObservableSet<Chapter> chapters = FXCollections.observableSet();
    // Created once: the view hears about changes only through a weak listener on the set, so a view made per call
    // would be garbage collected, and every listener added to it would silently stop firing.
    private final ObservableSet<Chapter> readOnlyChapters = FXCollections.unmodifiableObservableSet(chapters);

    void put(Chapter chapter, String text) {
        texts.put(chapter, text);
        chapters.add(chapter);
    }

    void remove(Chapter chapter) {
        texts.remove(chapter);
        chapters.remove(chapter);
    }

    Optional<String> text(Chapter chapter) {
        return Optional.ofNullable(texts.get(chapter));
    }

    boolean contains(Chapter chapter) {
        return texts.containsKey(chapter);
    }

    boolean isEmpty() {
        return texts.isEmpty();
    }

    /** The chapters with a draft; changes only when a chapter gains or loses one, not on every edit. */
    ObservableSet<Chapter> chapters() {
        return readOnlyChapters;
    }

    /** Chapters with a draft, in book order. */
    List<Chapter> sortedChapters() {
        return texts.keySet().stream().sorted(Comparator.comparing(Chapter::file)).toList();
    }

    /**
     * Writes every draft to its chapter file and forgets it. A chapter that cannot be written keeps its draft, and the
     * others are still saved.
     *
     * @throws UncheckedIOException for the first chapter that could not be written, with any later ones suppressed
     */
    void saveAll(BookFolder book) {

        UncheckedIOException failure = null;

        for (Chapter chapter : sortedChapters()) {
            try {
                book.write(chapter, texts.get(chapter));
                remove(chapter);
            } catch (UncheckedIOException e) {
                if (failure == null)
                    failure = e;
                else
                    failure.addSuppressed(e);
            }
        }

        if (failure != null)
            throw failure;
    }

    void clear() {
        texts.clear();
        chapters.clear();
    }
}
