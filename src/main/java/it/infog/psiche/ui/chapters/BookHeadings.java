package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.HeadingAnchor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The headings of every chapter of the book, which the open chapter's links are checked against and the link dialog
 * offers, from the chapters as saved, which {@link BookReading} reads. Used on the FX thread.
 */
final class BookHeadings {

    private final Consumer<Map<String, Set<String>>> read;
    private SequencedMap<String, List<HeadingAnchor>> headings = Collections.emptySortedMap();

    /**
     * @param read given each chapter's name with the anchors of its headings, every time they are read; {@code null}
     *             once they are dropped, as while no book is open
     */
    BookHeadings(Consumer<Map<String, Set<String>>> read) {

        this.read = read;
    }

    /** Takes {@code found}, each chapter's name, in the book's order, with its headings, as saved. */
    void take(SequencedMap<String, List<HeadingAnchor>> found) {

        headings = found;
        read.accept(anchors(found));
    }

    /** Drops the headings, as no book is open. */
    void clear() {

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
