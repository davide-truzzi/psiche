package it.infog.psiche.spelling.model.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The languages Psiche checks the spelling of, each with the Hunspell dictionary it brings in the app, one country's
 * for the whole language: a book in Swiss Italian or British English is checked with the Italian or the American one.
 */
public enum SpellingLanguage {

    ENGLISH("en", "en_US"),
    ITALIAN("it", "it_IT"),
    FRENCH("fr", "fr"),
    SPANISH("es", "es_ES"),
    GERMAN("de", "de_DE_frami");

    private final String language;
    private final String dictionary;

    /**
     * @param language   the language's ISO 639 code, which also names the dictionary's folder in the resources
     * @param dictionary the name of the dictionary's {@code .aff} and {@code .dic} files, without the extension
     */
    SpellingLanguage(String language, String dictionary) {

        this.language = language;
        this.dictionary = dictionary;
    }

    /** The language that checks a book written in {@code tag}, a BCP 47 tag such as {@code it-CH}; none for others. */
    public static Optional<SpellingLanguage> of(String tag) {

        String wanted = Locale.forLanguageTag(tag).getLanguage();
        return Arrays.stream(values()).filter(each -> each.language.equals(wanted)).findFirst();
    }

    /** The language, for the rules of its capital letters. */
    public Locale locale() {

        return Locale.of(language);
    }

    /** The dictionary's affix file, its rules, in the spelling resources, as {@code it/it_IT.aff}. */
    public String affixFile() {

        return "%s/%s.aff".formatted(language, dictionary);
    }

    /** The dictionary's word list in the spelling resources, as {@code it/it_IT.dic}. */
    public String wordsFile() {

        return "%s/%s.dic".formatted(language, dictionary);
    }
}
