package it.infog.psiche.export.docx;

import java.util.HashMap;
import java.util.Map;

/**
 * The bookmarks of a DOCX, which links lead to: each numbered in the document from 1, and named as Word takes them,
 * with letters, digits and underscores only, hidden as Word's own are.
 */
final class Bookmarks {

    // Word's names for the bookmarks, by the names they are given.
    private final Map<String, String> names = new HashMap<>();
    private int written;

    /** The bookmark {@code name}, to mark the place written next with. */
    String mark(String name) {

        written++;
        return "<w:bookmarkStart w:id=\"%d\" w:name=\"%s\"/><w:bookmarkEnd w:id=\"%d\"/>"
                .formatted(written, name(name), written);
    }

    /** Word's name for the bookmark {@code name}, the same each time it is asked for. */
    String name(String name) {

        return names.computeIfAbsent(name, given -> "_Bookmark" + (names.size() + 1));
    }
}
