package it.infog.psiche.spelling;

import it.infog.psiche.spelling.model.Word;
import it.infog.psiche.spelling.model.SpellingLanguage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpellCheckerTest {

    // Reading a dictionary takes a moment, so each the app brings is read once for all the tests, by its tag.
    private static final Map<String, SpellChecker> CHECKERS = new HashMap<>();

    @BeforeAll
    static void readEveryDictionaryTheAppBrings() {

        for (SpellingLanguage language : Dictionaries.bundled())
            CHECKERS.put(language.tag(), SpellChecker.load(language));
    }

    @ParameterizedTest
    @CsvSource({
            "en-US, house, hosue",
            "it-IT, casa, csa",
            "fr-FR, maison, maisn",
            "es-ES, canción, cancion",
            "de-DE, Straße, Strasse",
            "pt-BR, coração, coracao",
            "pt-PT, coração, coracao"})
    void eachDictionaryKnowsItsWordsAndNotAMisspeltOne(String language, String right, String wrong) {

        assertThat(right(language, right)).isTrue();
        assertThat(right(language, wrong)).isFalse();
    }

    @Test
    void brazilAndPortugalEachKnowTheirOwnSpelling() {

        assertThat(List.of("econômico", "fenômeno", "recepção")).allMatch(word -> right("pt-BR", word))
                .noneMatch(word -> right("pt-PT", word));
        assertThat(List.of("económico", "fenómeno", "receção")).allMatch(word -> right("pt-PT", word))
                .noneMatch(word -> right("pt-BR", word));
    }

    @Test
    void italianWordsWithApostrophesAreRightWithAStraightOrACurlyOne() {

        assertThat(List.of("dell'anima", "l'uomo", "un'altra", "Un'altra", "po'", "dell’anima", "l’uomo", "po’"))
                .allMatch(word -> right("it-IT", word));
    }

    @Test
    void frenchWordsWithApostrophesAndHyphensAreRight() {

        assertThat(List.of("l'été", "L'été", "l’été", "aujourd'hui", "qu'il", "peut-être"))
                .allMatch(word -> right("fr-FR", word));
    }

    @Test
    void germanCompoundsAreRight() {

        assertThat(List.of("Donaudampfschifffahrt", "Bundesverfassungsgericht", "Kindergarten"))
                .allMatch(word -> right("de-DE", word));
    }

    @Test
    void aCapitalAtTheStartOfASentenceAndCapitalsInAHeadingAreRight() {

        assertThat(List.of("Perché", "PERCHÉ", "Casa", "CASA")).allMatch(word -> right("it-IT", word));
        assertThat(right("it-IT", "roma")).isFalse();
    }

    @Test
    void aFinalApostropheThatClosesAQuoteIsLetGo() {

        assertThat(right("en-US", "house'")).isTrue();
        assertThat(right("en-US", "hosue'")).isFalse();
    }

    @Test
    void theAuthorsOwnWordsAreRightAsWrittenWithACapitalOrInCapitals() {

        Set<String> own = Set.of("Elric", "drago");
        SpellChecker english = CHECKERS.get("en-US");

        assertThat(List.of("Elric", "ELRIC", "drago", "Drago", "DRAGO")).allMatch(word -> english.isRight(word, own));
        assertThat(english.isRight("elric", own)).isFalse();
        assertThat(english.isRight("Elrik", own)).isFalse();
    }

    @Test
    void anOwnWordWithAStraightApostropheCoversTheCurlyOne() {

        assertThat(CHECKERS.get("en-US").isRight("Ma’ar", Set.of("Ma'ar"))).isTrue();
    }

    @Test
    void suggestsTheLikeliestWordsFirstAndNoBarePartsOfCompounds() {

        assertThat(CHECKERS.get("it-IT").suggestions("perchè")).first().isEqualTo("perché");
        assertThat(CHECKERS.get("en-US").suggestions("recieve")).first().isEqualTo("receive");
        assertThat(CHECKERS.get("de-DE").suggestions("haus"))
                .contains("Haus")
                .noneMatch(suggestion -> suggestion.startsWith("-"));
        assertThat(CHECKERS.get("es-ES").suggestions("cancion")).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void aSuggestionKeepsTheCurlyApostropheOfTheWord() {

        assertThat(CHECKERS.get("it-IT").suggestions("dell’anma")).contains("dell’anima");
    }

    @Test
    void findsTheMistakesOfAChaptersProseOnly() {

        String markdown = "# Alba\n\nIt began at dwan <!-- a nnote -->, `teh code`, when Elric left.\n";

        List<Word> mistakes = CHECKERS.get("en-US").mistakes(markdown, Set.of("Elric"), true);

        assertThat(mistakes).extracting(Word::text).containsExactly("dwan");
        assertThat(markdown.substring(mistakes.getFirst().range().start(), mistakes.getFirst().range().end()))
                .isEqualTo("dwan");
    }

    @Test
    void wordsThatLookLikeNamesAreLeftOutUnlessStrict() {

        String markdown = "Gabriele D'Annunzio, said d'Annunzio, met Elric at dwan near Montenevoso.\n";
        SpellChecker english = CHECKERS.get("en-US");

        assertThat(english.mistakes(markdown, Set.of(), false)).extracting(Word::text).containsExactly("dwan");
        assertThat(english.mistakes(markdown, Set.of(), true)).extracting(Word::text)
                .containsExactly("Gabriele", "D'Annunzio", "d'Annunzio", "Elric", "dwan", "Montenevoso");
    }

    @Test
    void aMisspeltWordAtTheStartOfASentenceIsLeftOutUnlessStrict() {

        SpellChecker english = CHECKERS.get("en-US");

        assertThat(english.mistakes("Teh carts left.\n", Set.of(), false)).isEmpty();
        assertThat(english.mistakes("Teh carts left.\n", Set.of(), true)).extracting(Word::text).containsExactly("Teh");
    }

    private static boolean right(String language, String word) {

        return CHECKERS.get(language).isRight(word, Set.of());
    }
}
