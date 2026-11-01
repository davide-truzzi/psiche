package it.infog.psiche.book.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PageMeasureTest {

    @ParameterizedTest
    @CsvSource({
            "CARTELLA,   3600, 999, 2.0 cartelle",
            "FEUILLET,   2250, 999, 1.5 feuillets",
            "STANDARD,   999,  450, 1.5 pages",
            "MANUSCRIPT, 999,  250, 1.0 page",
            "PAPERBACK,  999,  700, 2.0 pages",
    })
    void dividesCharactersOrWordsByWhatAPageHolds(PageMeasure measure, int characters, int words, String expected) {
        assertThat(measure.describe(characters, words)).isEqualTo(expected);
    }

    @Test
    void roundsToOneDecimal() {
        assertThat(PageMeasure.CARTELLA.describe(1000, 0)).isEqualTo("0.6 cartelle");
        assertThat(PageMeasure.CARTELLA.describe(0, 0)).isEqualTo("0.0 cartelle");
    }

    @Test
    void aTextTooShortForATenthOfAPageIsLessThanATenth() {
        assertThat(PageMeasure.CARTELLA.describe(21, 5)).isEqualTo("< 0.1 cartelle");
        assertThat(PageMeasure.STANDARD.describe(21, 5)).isEqualTo("< 0.1 pages");
        assertThat(PageMeasure.STANDARD.describe(90, 15)).isEqualTo("0.1 pages");
    }

    @ParameterizedTest
    @CsvSource({"it-IT, CARTELLA", "it, CARTELLA", "fr-CA, FEUILLET", "en-GB, STANDARD", "de-DE, STANDARD", "'', STANDARD"})
    void suggestsTheMeasureOfTheBooksLanguage(String language, PageMeasure expected) {
        assertThat(PageMeasure.forLanguage(language)).isEqualTo(expected);
    }

    @Test
    void readsBackFromItsKey() {
        for (PageMeasure measure : PageMeasure.values())
            assertThat(PageMeasure.fromKey(measure.key())).contains(measure);
        assertThat(PageMeasure.fromKey("folio")).isEmpty();
    }
}
