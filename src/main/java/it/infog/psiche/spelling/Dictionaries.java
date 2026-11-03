package it.infog.psiche.spelling;

import it.infog.psiche.spelling.model.SpellingLanguage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The spelling dictionaries the app brings, as {@value #INDEX} in the spelling resources lists them, one a line by
 * BCP 47 tag, in the order a language's dictionaries are preferred. Adding a dictionary, or removing one, takes its
 * folder and its line there, and no code: see {@code SOURCES.md} beside it.
 * <p>
 * A book is checked with the dictionary of its language and country, or else with the first one listed for its
 * language: a book in Portuguese from Portugal with the Portuguese one, from Angola with the first Portuguese one
 * listed, in Swiss Italian with the Italian one.
 */
public final class Dictionaries {

    static final String INDEX = "dictionaries.txt";
    private static final String COMMENT = "#";

    private Dictionaries() {
    }

    /** Every dictionary the app brings, in the order {@value #INDEX} lists them. */
    public static List<SpellingLanguage> bundled() {

        return Bundled.LANGUAGES;
    }

    /** The dictionary a book written in {@code tag}, a BCP 47 tag such as {@code it-CH}, is checked with, if any. */
    public static Optional<SpellingLanguage> forBook(String tag) {

        Locale book = Locale.forLanguageTag(tag);
        return bundled().stream()
                .filter(language -> language.locale().equals(book))
                .findFirst()
                .or(() -> bundled().stream()
                        .filter(language -> language.locale().getLanguage().equals(book.getLanguage()))
                        .findFirst());
    }

    /**
     * The dictionaries {@code index} lists, one tag a line; blank lines and those starting with {@value #COMMENT} are
     * notes.
     *
     * @throws IllegalStateException if a line is no well-formed language tag, which only a build can cause
     */
    static List<SpellingLanguage> read(BufferedReader index) {

        return index.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT))
                .map(Dictionaries::language)
                .toList();
    }

    private static SpellingLanguage language(String tag) {

        Locale locale = Locale.forLanguageTag(tag);
        if (locale.getLanguage().isEmpty() || !locale.toLanguageTag().equals(tag))
            throw new IllegalStateException("'%s' in the spelling %s is no language tag".formatted(tag, INDEX));

        return new SpellingLanguage(locale);
    }

    /** The index, read once, the first time a dictionary is asked for. */
    private static final class Bundled {

        static final List<SpellingLanguage> LANGUAGES = readIndex();

        private static List<SpellingLanguage> readIndex() {

            InputStream in = Dictionaries.class.getResourceAsStream(INDEX);
            if (in == null)
                throw new IllegalStateException("The app has no spelling " + INDEX);

            try (BufferedReader index = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return read(index);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read the spelling " + INDEX, e);
            }
        }
    }
}
