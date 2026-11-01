package it.infog.psiche.ui.git;

import it.infog.psiche.git.TrackedChanges;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RevisionDialogTest {

    @Test
    void saysWhenTheVersionIsTheSameAsTheSavedChapter() {
        assertThat(RevisionDialog.describe(TrackedChanges.compare("It began.\n", "It began.\n")))
                .isEqualTo("This version is the same as your saved chapter.");
    }

    @Test
    void countsParagraphsInWords() {
        String then = "# Departure\n\nbu!\n\nNobody spoke.\n";
        String now = "# Departure\n\nbu!dd\n\nNobody spoke.\n\nLectus primis.\n\nThe gate opened.\n";

        assertThat(RevisionDialog.describe(TrackedChanges.compare(then, now)))
                .isEqualTo("Since this version you changed 1 paragraph and added 2.");
    }

    @Test
    void namesTheParagraphsOnlyOnce() {
        String then = "It began.\nNobody spoke.\nThe gate opened.\nRain.\n";
        String now = "It began at dusk.\nThe gate opened.\nRain.\nWind.\n";

        assertThat(RevisionDialog.describe(TrackedChanges.compare(then, now)))
                .isEqualTo("Since this version you changed 1 paragraph, added 1 and removed 1.");
    }

    @Test
    void mentionsSpacingWhenNoParagraphChanged() {
        assertThat(RevisionDialog.describe(TrackedChanges.compare("It began.\n", "It began.\n\n")))
                .isEqualTo("Since this version only blank lines and spacing have changed.");
    }
}
