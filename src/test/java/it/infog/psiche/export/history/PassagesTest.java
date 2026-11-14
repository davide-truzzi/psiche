package it.infog.psiche.export.history;

import it.infog.psiche.export.history.model.Passage;
import it.infog.psiche.git.TrackedChanges;
import it.infog.psiche.git.model.Comparison;
import it.infog.psiche.git.model.Paragraph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PassagesTest {

    @Test
    void aChangeComesWithOneUnchangedParagraphBeforeAndAfterIt() {
        Comparison comparison = TrackedChanges.compare(text(1, 2, 3, 4, 5), text(1, 2, 3).replace("Line 3.",
                "Line three.") + text(4, 5));

        List<Passage> passages = Passages.of(comparison);

        assertThat(passages).hasSize(1);
        assertThat(shown(comparison, passages.getFirst())).isEqualTo("Line 2. | Line [-3-]{+three+}. | Line 4.");
    }

    @Test
    void blankLinesAreNotCountedAsContext() {
        Comparison comparison = TrackedChanges.compare("One.\n\nTwo.\n\nThree.\n", "One.\n\nTwo, again.\n\nThree.\n");

        assertThat(shown(comparison, Passages.of(comparison).getFirst())).isEqualTo("One. | Two{+, again+}. | Three.");
    }

    @Test
    void changesWhoseContextWouldTouchShareOnePassage() {
        String before = text(1, 2, 3, 4, 5, 6, 7);
        String after = before.replace("Line 2.", "Line two.").replace("Line 5.", "Line five.");

        List<Passage> passages = Passages.of(TrackedChanges.compare(before, after));

        assertThat(passages).extracting(Passage::from, Passage::to).containsExactly(tuple(0, 6));
    }

    @Test
    void changesFartherApartAreSeparatePassages() {
        String before = text(1, 2, 3, 4, 5, 6, 7, 8);
        String after = before.replace("Line 2.", "Line two.").replace("Line 7.", "Line seven.");

        List<Passage> passages = Passages.of(TrackedChanges.compare(before, after));

        assertThat(passages).extracting(Passage::from, Passage::to).containsExactly(
                tuple(0, 3), tuple(5, 8));
    }

    @Test
    void aNewChapterIsOnePassageOfAllItsText() {
        Comparison comparison = TrackedChanges.compare("", "# Dawn\n\nIt began.\n\nNobody spoke.\n");

        List<Passage> passages = Passages.of(comparison);

        assertThat(passages).containsExactly(new Passage(0, 5, 5));
    }

    @Test
    void changedSpacesAndBlankLinesMakeNoPassage() {
        assertThat(Passages.of(TrackedChanges.compare("One two.\nThree.\n", "One  two.\n\nThree.\n"))).isEmpty();
    }

    @Test
    void aPassageCountsTheWordsWrittenAndRemoved() {
        Comparison comparison = TrackedChanges.compare("It began at dawn.\n", "It began, slowly, at dusk.\n");

        assertThat(Passages.of(comparison).getFirst().words()).isEqualTo(3);
    }

    /** Lines "Line n." for each n, one per line. */
    private static String text(int... lines) {
        return IntStream.of(lines).mapToObj(n -> "Line " + n + ".\n").collect(Collectors.joining());
    }

    /** The passage's paragraphs with text, removed text as [-this-] and written text as {+this+}, joined by " | ". */
    private static String shown(Comparison comparison, Passage passage) {
        return comparison.paragraphs().subList(passage.from(), passage.to()).stream()
                .filter(Passages::hasText)
                .map(PassagesTest::marked)
                .collect(Collectors.joining(" | "));
    }

    private static String marked(Paragraph paragraph) {
        return paragraph.segments().stream().map(segment -> switch (segment.change()) {
            case UNCHANGED -> segment.text();
            case REMOVED -> "[-" + segment.text() + "-]";
            case ADDED -> "{+" + segment.text() + "+}";
        }).collect(Collectors.joining());
    }
}
