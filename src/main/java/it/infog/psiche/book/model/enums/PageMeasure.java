package it.infog.psiche.book.model.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The ways a chapter's length can be told in pages, as publishers in different countries count them. */
public enum PageMeasure {

    STANDARD("Standard (300 words)", "page", "pages", false, 300),
    CARTELLA("Cartella (IT)", "cartella", "cartelle", true, 1800),
    FEUILLET("Feuillet (FR)", "feuillet", "feuillets", true, 1500),
    MANUSCRIPT("Manuscript (250 words)", "page", "pages", false, 250),
    PAPERBACK("Printed paperback", "page", "pages", false, 350);

    private final String label;
    private final String singular;
    private final String plural;
    private final boolean byCharacters;
    private final int perPage;

    PageMeasure(String label, String singular, String plural, boolean byCharacters, int perPage) {

        this.label = label;
        this.singular = singular;
        this.plural = plural;
        this.byCharacters = byCharacters;
        this.perPage = perPage;
    }

    /** The measure publishers use for a book in {@code languageTag}: cartelle in Italian, feuillets in French. */
    public static PageMeasure forLanguage(String languageTag) {

        String language = Locale.forLanguageTag(languageTag).getLanguage();
        return switch (language) {
            case "it" -> CARTELLA;
            case "fr" -> FEUILLET;
            default -> STANDARD;
        };
    }

    /** The measure saved under {@code key}; empty if no measure has that key. */
    public static Optional<PageMeasure> fromKey(String key) {

        return Arrays.stream(values()).filter(measure -> measure.key().equals(key)).findFirst();
    }

    /** The name the measure is saved under in the book's properties. */
    public String key() {

        return name().toLowerCase(Locale.ROOT);
    }

    /** The name the author picks the measure by. */
    public String label() {

        return label;
    }

    /** Characters with spaces divided by the characters of a page, or words by the words of a page. */
    public double pages(int characters, int words) {

        return (double) (byCharacters ? characters : words) / perPage;
    }

    /**
     * The pages to one decimal with their unit, such as "2.4 cartelle" or "1.0 page". A text too short to round up
     * to a tenth of a page is "< 0.1 pages", so only an empty one shows none.
     */
    public String describe(int characters, int words) {

        double count = pages(characters, words);
        String pages = String.format(Locale.ENGLISH, "%.1f", count);
        if (count > 0 && pages.equals("0.0"))
            pages = "< 0.1";

        return pages + " " + (pages.equals("1.0") ? singular : plural);
    }
}
