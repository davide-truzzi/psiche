package it.infog.psiche.ui.chapters;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FastRepetitionsTest {

    @Test
    void theParagraphsLookedInStartTwoBeforeTheCaretsOne() {

        assertThat(FastRepetitions.firstLookedIn(10, 30)).isEqualTo(8);
    }

    @Test
    void nearTheTopOfTheViewTheyStartAtItsFirstParagraph() {

        assertThat(FastRepetitions.firstLookedIn(1, 30)).isEqualTo(0);
        assertThat(FastRepetitions.firstLookedIn(0, 30)).isEqualTo(0);
    }

    @Test
    void nearTheBottomOfTheViewTheyEndAtItsLastParagraph() {

        assertThat(FastRepetitions.firstLookedIn(29, 30)).isEqualTo(26);
        assertThat(FastRepetitions.firstLookedIn(28, 30)).isEqualTo(26);
    }

    @Test
    void aViewOfFewerParagraphsIsLookedInWhole() {

        assertThat(FastRepetitions.firstLookedIn(2, 3)).isEqualTo(0);
        assertThat(FastRepetitions.firstLookedIn(0, 1)).isEqualTo(0);
    }
}
