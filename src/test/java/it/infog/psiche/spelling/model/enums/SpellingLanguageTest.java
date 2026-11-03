package it.infog.psiche.spelling.model.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SpellingLanguageTest {

    @ParameterizedTest
    @CsvSource({
            "en-US, ENGLISH",
            "en-GB, ENGLISH",
            "it-IT, ITALIAN",
            "it-CH, ITALIAN",
            "fr-CA, FRENCH",
            "es-MX, SPANISH",
            "de-AT, GERMAN",
            "de, GERMAN"})
    void aBookIsCheckedInItsLanguageWhateverTheCountry(String tag, SpellingLanguage language) {

        assertThat(SpellingLanguage.of(tag)).contains(language);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pt-BR", "nl-NL", "ja-JP", ""})
    void aLanguageWithoutADictionaryHasNone(String tag) {

        assertThat(SpellingLanguage.of(tag)).isEmpty();
    }
}
