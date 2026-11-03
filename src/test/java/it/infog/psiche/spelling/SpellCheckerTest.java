package it.infog.psiche.spelling;

import it.infog.psiche.spelling.model.Word;
import it.infog.psiche.spelling.model.enums.SpellingLanguage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpellCheckerTest {

    // Reading a dictionary takes a moment, so each is read once for all the tests.
    private static final Map<SpellingLanguage, SpellChecker> CHECKERS = new EnumMap<>(SpellingLanguage.class);

    @BeforeAll
    static void readTheDictionaries() {

        for (SpellingLanguage language : SpellingLanguage.values())
            CHECKERS.put(language, SpellChecker.load(language));
    }

    @ParameterizedTest
    @CsvSource({
            "ENGLISH, house, hosue",
            "ITALIAN, casa, csa",
            "FRENCH, maison, maisn",
            "SPANISH, canción, cancion",
            "GERMAN, Straße, Strasse"})
    void eachDictionaryKnowsItsWordsAndNotAMisspeltOne(SpellingLanguage language, String right, String wrong) {

        assertThat(right(language, right)).isTrue();
        assertThat(right(language, wrong)).isFalse();
    }

    @Test
    void italianWordsWithApostrophesAreRightWithAStraightOrACurlyOne() {

        assertThat(List.of("dell'anima", "l'uomo", "un'altra", "Un'altra", "po'", "dell’anima", "l’uomo", "po’"))
                .allMatch(word -> right(SpellingLanguage.ITALIAN, word));
    }

    @Test
    void frenchWordsWithApostrophesAndHyphensAreRight() {

        assertThat(List.of("l'été", "L'été", "l’été", "aujourd'hui", "qu'il", "peut-être"))
                .allMatch(word -> right(SpellingLanguage.FRENCH, word));
    }

    @Test
    void germanCompoundsAreRight() {

        assertThat(List.of("Donaudampfschifffahrt", "Bundesverfassungsgericht", "Kindergarten"))
                .allMatch(word -> right(SpellingLanguage.GERMAN, word));
    }

    @Test
    void aCapitalAtTheStartOfASentenceAndCapitalsInAHeadingAreRight() {

        assertThat(List.of("Perché", "PERCHÉ", "Casa", "CASA")).allMatch(word -> right(SpellingLanguage.ITALIAN, word));
        assertThat(right(SpellingLanguage.ITALIAN, "roma")).isFalse();
    }

    @Test
    void aFinalApostropheThatClosesAQuoteIsLetGo() {

        assertThat(right(SpellingLanguage.ENGLISH, "house'")).isTrue();
        assertThat(right(SpellingLanguage.ENGLISH, "hosue'")).isFalse();
    }

    @Test
    void theAuthorsOwnWordsAreRightAsWrittenWithACapitalOrInCapitals() {

        Set<String> own = Set.of("Elric", "drago");
        SpellChecker english = CHECKERS.get(SpellingLanguage.ENGLISH);

        assertThat(List.of("Elric", "ELRIC", "drago", "Drago", "DRAGO")).allMatch(word -> english.isRight(word, own));
        assertThat(english.isRight("elric", own)).isFalse();
        assertThat(english.isRight("Elrik", own)).isFalse();
    }

    @Test
    void anOwnWordWithAStraightApostropheCoversTheCurlyOne() {

        assertThat(CHECKERS.get(SpellingLanguage.ENGLISH).isRight("Ma’ar", Set.of("Ma'ar"))).isTrue();
    }

    @Test
    void suggestsTheLikeliestWordsFirstAndNoBarePartsOfCompounds() {

        assertThat(CHECKERS.get(SpellingLanguage.ITALIAN).suggestions("perchè")).first().isEqualTo("perché");
        assertThat(CHECKERS.get(SpellingLanguage.ENGLISH).suggestions("recieve")).first().isEqualTo("receive");
        assertThat(CHECKERS.get(SpellingLanguage.GERMAN).suggestions("haus"))
                .contains("Haus")
                .noneMatch(suggestion -> suggestion.startsWith("-"));
        assertThat(CHECKERS.get(SpellingLanguage.SPANISH).suggestions("cancion")).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void aSuggestionKeepsTheCurlyApostropheOfTheWord() {

        assertThat(CHECKERS.get(SpellingLanguage.ITALIAN).suggestions("dell’anma")).contains("dell’anima");
    }

    @Test
    void findsTheMistakesOfAChaptersProseOnly() {

        String markdown = "# Alba\n\nIt began at dwan <!-- a nnote -->, `teh code`, when Elric left.\n";

        List<Word> mistakes = CHECKERS.get(SpellingLanguage.ENGLISH).mistakes(markdown, Set.of("Elric"));

        assertThat(mistakes).extracting(Word::text).containsExactly("dwan");
        assertThat(markdown.substring(mistakes.getFirst().range().start(), mistakes.getFirst().range().end()))
                .isEqualTo("dwan");
    }

    private static boolean right(SpellingLanguage language, String word) {

        return CHECKERS.get(language).isRight(word, Set.of());
    }
}
