package it.infog.psiche.export.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The book's table of contents, decided once for every format: a heading in the book's language, then a line per
 * chapter, in order. Each format only decides how to draw it, and how a line leads to its chapter.
 *
 * @param heading the contents' heading, such as "Indice" in an Italian book
 * @param entries a line per chapter, in the order of the book
 */
public record Contents(String heading,
                       List<Entry> entries) {

    // The heading in the languages Psiche Writer knows; English for any other.
    private static final Map<String, String> HEADINGS = Map.of(
            "en", "Contents",
            "it", "Indice",
            "fr", "Table des matières",
            "de", "Inhalt",
            "es", "Índice",
            "pt", "Sumário",
            "nl", "Inhoud");

    /**
     * One line of the contents.
     *
     * @param id    the chapter's id, such as {@code chapter-3}, which marks where the chapter starts in every format
     * @param title the chapter's title, as its first heading has it
     */
    public record Entry(String id,
                        String title) {
    }

    public Contents {

        entries = List.copyOf(entries);
    }

    /**
     * The contents of a book in {@code language}, a BCP 47 tag such as {@code it-IT}: a line per chapter, with the
     * chapter's id and title.
     */
    public static Contents of(String language, List<ExportedChapter> chapters) {

        String heading = HEADINGS.getOrDefault(Locale.forLanguageTag(language).getLanguage(), HEADINGS.get("en"));
        List<Entry> entries = chapters.stream().map(chapter -> new Entry(chapter.id(), chapter.title())).toList();

        return new Contents(heading, entries);
    }
}
