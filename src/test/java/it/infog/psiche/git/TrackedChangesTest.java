package it.infog.psiche.git;

import it.infog.psiche.git.model.Comparison;
import it.infog.psiche.git.model.Paragraph;
import it.infog.psiche.git.model.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static it.infog.psiche.git.model.Segment.Change.ADDED;
import static it.infog.psiche.git.model.Segment.Change.REMOVED;
import static it.infog.psiche.git.model.Segment.Change.UNCHANGED;
import static org.assertj.core.api.Assertions.assertThat;

class TrackedChangesTest {

    @Test
    void identicalTextsHaveNoChanges() {
        String text = "# Departure\n\nIt began at dawn.\n";

        Comparison comparison = TrackedChanges.compare(text, text);

        assertThat(comparison.identical()).isTrue();
        assertThat(comparison.paragraphs()).containsExactly(
                paragraph(new Segment("# Departure", UNCHANGED)),
                paragraph(new Segment("", UNCHANGED)),
                paragraph(new Segment("It began at dawn.", UNCHANGED)));
        assertThat(List.of(comparison.changed(), comparison.added(), comparison.removed())).containsOnly(0);
    }

    @Test
    void countsTheWordsWrittenAndRemovedButNotPunctuationOrSpaces() {
        Comparison comparison = TrackedChanges.compare("It began at dawn.\nNobody spoke.\n",
                "It began, slowly, at dusk.\nNobody spoke.\nThe carts were loaded.\n");

        assertThat(comparison.wordsAdded()).isEqualTo(6);
        assertThat(comparison.wordsRemoved()).isEqualTo(1);
    }

    @Test
    void countsWordsAsAWriterDoes() {
        assertThat(TrackedChanges.countWords("L'alba, 3 carri — e nessuno parlava...")).isEqualTo(6);
    }

    @Test
    void marksOnlyTheWordsWrittenSinceTheVersion() {
        Comparison comparison = TrackedChanges.compare("bu!\n", "bu!dd\n");

        assertThat(comparison.paragraphs()).containsExactly(
                paragraph(new Segment("bu!", UNCHANGED), new Segment("dd", ADDED)));
        assertThat(comparison.changed()).isEqualTo(1);
    }

    @Test
    void marksAReplacedWordAsRemovedThenWritten() {
        Comparison comparison = TrackedChanges.compare("It began at dawn, in the rain.", "It began at dusk, in the rain.");

        assertThat(comparison.paragraphs()).containsExactly(paragraph(
                new Segment("It began at ", UNCHANGED),
                new Segment("dawn", REMOVED),
                new Segment("dusk", ADDED),
                new Segment(", in the rain.", UNCHANGED)));
    }

    @Test
    void joinsChangesSeparatedOnlyBySpaces() {
        Comparison comparison = TrackedChanges.compare(
                "She saw the old grey cat by the well.", "She saw a new black cat by the well.");

        assertThat(comparison.paragraphs()).containsExactly(paragraph(
                new Segment("She saw ", UNCHANGED),
                new Segment("the old grey", REMOVED),
                new Segment("a new black", ADDED),
                new Segment(" cat by the well.", UNCHANGED)));
    }

    @Test
    void keepsParagraphsWrittenSinceTheVersionInPlace() {
        Comparison comparison = TrackedChanges.compare(
                "# Departure\n\nbu!\n", "# Departure\n\nbu!dd\n\nLectus primis volutpat.\n");

        assertThat(comparison.paragraphs()).containsExactly(
                paragraph(new Segment("# Departure", UNCHANGED)),
                paragraph(new Segment("", UNCHANGED)),
                paragraph(new Segment("bu!", UNCHANGED), new Segment("dd", ADDED)),
                paragraph(new Segment("", ADDED)),
                paragraph(new Segment("Lectus primis volutpat.", ADDED)));
        assertThat(comparison.changed()).isEqualTo(1);
        assertThat(comparison.added()).isEqualTo(1);
        assertThat(comparison.removed()).isZero();
    }

    @Test
    void keepsParagraphsRemovedSinceTheVersionInPlace() {
        Comparison comparison = TrackedChanges.compare(
                "It began at dawn.\nNobody spoke.\nThe gate opened.\n", "It began at dawn.\nThe gate opened.\n");

        assertThat(comparison.paragraphs()).containsExactly(
                paragraph(new Segment("It began at dawn.", UNCHANGED)),
                paragraph(new Segment("Nobody spoke.", REMOVED)),
                paragraph(new Segment("The gate opened.", UNCHANGED)));
        assertThat(comparison.removed()).isEqualTo(1);
    }

    @Test
    void showsARewrittenParagraphAsTheOldOneStruckThenTheNewOne() {
        Comparison comparison = TrackedChanges.compare(
                "The caravan left at dawn.\n", "Nobody remembered why the wagons turned back.\n");

        assertThat(comparison.paragraphs()).containsExactly(
                paragraph(new Segment("The caravan left at dawn.", REMOVED)),
                paragraph(new Segment("Nobody remembered why the wagons turned back.", ADDED)));
        assertThat(comparison.removed()).isEqualTo(1);
        assertThat(comparison.added()).isEqualTo(1);
        assertThat(comparison.changed()).isZero();
    }

    @Test
    void comparesAnInsertedLineAsWholeRatherThanWithItsNeighbour() {
        Comparison comparison = TrackedChanges.compare(
                "- si versiona su GIT e puoi sempre tornare indietro\n",
                "- crea file di testo esportabili\n- si versiona su GIT e puoi sempre tornare indietro a versioni precedenti\n");

        assertThat(comparison.paragraphs()).containsExactly(
                paragraph(new Segment("- crea file di testo esportabili", ADDED)),
                paragraph(new Segment("- si versiona su GIT e puoi sempre tornare indietro", UNCHANGED),
                        new Segment(" a versioni precedenti", ADDED)));
        assertThat(comparison.added()).isEqualTo(1);
        assertThat(comparison.changed()).isEqualTo(1);
    }

    @Test
    void pairsAJoinedParagraphWithTheLineItGrewFrom() {
        Comparison comparison = TrackedChanges.compare("It began at dawn.\nNobody spoke.", "It began at dawn. Nobody spoke.");

        assertThat(comparison.paragraphs()).containsExactly(
                paragraph(new Segment("It began at dawn.", UNCHANGED), new Segment(" Nobody spoke.", ADDED)),
                paragraph(new Segment("Nobody spoke.", REMOVED)));
    }

    @Test
    void everythingIsWrittenSinceAVersionThatWasEmpty() {
        Comparison comparison = TrackedChanges.compare("", "It began at dawn.\nNobody spoke.");

        assertThat(comparison.paragraphs()).containsExactly(
                paragraph(new Segment("It began at dawn.", ADDED)), paragraph(new Segment("Nobody spoke.", ADDED)));
        assertThat(comparison.added()).isEqualTo(2);
    }

    @Test
    void everythingIsRemovedWhenTheChapterIsNowEmpty() {
        Comparison comparison = TrackedChanges.compare("It began at dawn.\n", "");

        assertThat(comparison.paragraphs()).containsExactly(paragraph(new Segment("It began at dawn.", REMOVED)));
        assertThat(comparison.removed()).isEqualTo(1);
    }

    @Test
    void windowsLineEndingsAreNotPartOfTheText() {
        Comparison comparison = TrackedChanges.compare("It began at dawn.\r\nNobody spoke.\r\n", "It began at dawn.\nNobody spoke.\n");

        assertThat(comparison.identical()).isTrue();
    }

    private static Paragraph paragraph(Segment... segments) {
        return new Paragraph(List.of(segments));
    }
}
