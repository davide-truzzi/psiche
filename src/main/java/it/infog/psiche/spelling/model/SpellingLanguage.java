package it.infog.psiche.spelling.model;

import java.util.Locale;

/**
 * A language Psiche checks the spelling of, with the Hunspell dictionary the app brings for it: the folder of its tag
 * in the spelling resources, holding {@code <tag>.aff}, its rules, and {@code <tag>.dic}, its words.
 *
 * @param locale  the language and the country its dictionary is for, such as Portuguese (Brazil)
 * @param source  who made the dictionary, and its version, as "LibreItalia, 5.1.1"
 * @param licence the licence the app uses the dictionary under
 */
public record SpellingLanguage(Locale locale,
                               String source,
                               String licence) {

    /** Its BCP 47 tag, such as {@code pt-BR}, which also names its folder and its files. */
    public String tag() {

        return locale.toLanguageTag();
    }

    /** The dictionary's affix file, its rules, in the spelling resources, as {@code it-IT/it-IT.aff}. */
    public String affixFile() {

        return "%s/%s.aff".formatted(tag(), tag());
    }

    /** The dictionary's word list in the spelling resources, as {@code it-IT/it-IT.dic}. */
    public String wordsFile() {

        return "%s/%s.dic".formatted(tag(), tag());
    }
}
