package it.infog.psiche.spelling;

import it.infog.psiche.spelling.model.Word;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProseWordsTest {

    @Test
    void findsTheWordsOfParagraphsHeadingsListsAndQuotes() {

        assertThat(words("# Alba\n\nIt began *at* dawn.\n\n- carts\n- **mules**\n\n> Nobody spoke.\n"))
                .containsExactly("Alba", "It", "began", "at", "dawn", "carts", "mules", "Nobody", "spoke");
    }

    @Test
    void keepsTheApostrophesAndHyphensInsideAWordAndAFinalApostrophe() {

        assertThat(words("Dell'anima, l’uomo e un po' di peut-être.\n"))
                .containsExactly("Dell'anima", "l’uomo", "e", "un", "po'", "di", "peut-être");
    }

    @Test
    void leavesOutCodeCommentsAndHtml() {

        assertThat(words("Write `teh code` here <!-- a nnote --> and <span>thiss</span>.\n\n```\nfenced wrods\n```\n"))
                .containsExactly("Write", "here", "and", "thiss");
    }

    @Test
    void checksAnImageLinesCaptionAsTheBookPrintsItButNeverItsFile() {

        assertThat(words("Prima.\n\n![Eye of Sauron](../media/01-lorem/2025-07-23_15-24-36_pp.png)\n\n"
                + "![](../media/01-lorem/vecchia_mappa.png)\n\n![Mappa](<../media/01 lorem/my_map.png>)\n"))
                .containsExactly("Prima", "Eye", "of", "Sauron", "Mappa");
    }

    @Test
    void checksALinksTextButNotWhereItGoes() {

        assertThat(words("See [the map](media/mappa-vecchia.png) and ![a castle](media/castello.jpg).\n"))
                .containsExactly("See", "the", "map", "and", "a", "castle");
    }

    @Test
    void checksAChapterLinksTextButNotTheChapterOrHeadingItLeadsTo() {

        assertThat(words("Back to [the storm](<02 la tempestta.md#la-tempestta>) and [here](#unna-scena).\n"))
                .containsExactly("Back", "to", "the", "storm", "and", "here");
    }

    @Test
    void leavesOutAddressesEntitiesAndWordsWithDigits() {

        assertThat(words("Mail me@example.com or see www.example.com, <https://psiche.org/a> &amp; the B2 mp3.\n"))
                .containsExactly("Mail", "or", "see", "the");
    }

    @Test
    void knowsWhereEachWordIs() {

        String markdown = "## Due\n\nMarco *lied*.\n";

        assertThat(ProseWords.of(markdown))
                .allSatisfy(word -> assertThat(markdown.substring(word.range().start(), word.range().end()))
                        .isEqualTo(word.text()));
    }

    @Test
    void checksTheFootnotesTextButNotTheirLabels() {

        assertThat(words("Luna[^moonlight] piena.\n\n[^moonlight]: Una nota.\n"))
                .containsExactly("Luna", "piena", "Una", "nota");
    }

    @Test
    void checksAWordSplitByASuperscriptAsOneOverItsSigns() {

        String markdown = "La M^lle^ e il 1^st^ e H~2~O.";

        assertThat(ProseWords.of(markdown)).first().satisfies(word -> assertThat(word.text()).isEqualTo("La"));
        assertThat(ProseWords.of(markdown).get(1)).satisfies(word -> {
            assertThat(word.text()).isEqualTo("Mlle");
            assertThat(markdown.substring(word.range().start(), word.range().end())).isEqualTo("M^lle^");
        });
        assertThat(words(markdown)).containsExactly("La", "Mlle", "e", "il", "e");
    }

    @Test
    void anEmptyChapterHasNoWords() {

        assertThat(ProseWords.of("")).isEmpty();
    }

    private static List<String> words(String markdown) {

        return ProseWords.of(markdown).stream().map(Word::text).toList();
    }
}
