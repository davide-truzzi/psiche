package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.HeadingAnchor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeadingAnchorsTest {

    @Test
    void anAnchorIsTheHeadingInLowerCaseWithHyphensForSpacesAndNoPunctuation() {

        assertThat(HeadingAnchors.anchor("The Storm, at Last!")).isEqualTo("the-storm-at-last");
        assertThat(HeadingAnchors.anchor("Dell'anima: è già l'alba")).isEqualTo("dellanima-è-già-lalba");
        assertThat(HeadingAnchors.anchor("snake_case and kebab-case 2")).isEqualTo("snake_case-and-kebab-case-2");
    }

    @Test
    void findsEveryHeadingInOrderWithItsLevelAndTextAsTheReaderSeesIt() {

        String text = "# Departure\n\nAnna left.\n\n## The *old* harbour\n\nText.\n\nA setext one\n---\n";

        assertThat(HeadingAnchors.of(text)).containsExactly(
                new HeadingAnchor(1, "Departure", "departure"),
                new HeadingAnchor(2, "The old harbour", "the-old-harbour"),
                new HeadingAnchor(2, "A setext one", "a-setext-one"));
    }

    @Test
    void aRepeatedHeadingGetsTheNextNumberAsOnGitHub() {

        String text = "## Night\n\n## Night\n\n## Night\n";

        assertThat(HeadingAnchors.anchors(text)).containsExactly("night", "night-1", "night-2");
    }

    @Test
    void aHeadingInsideACommentOrCodeIsNoHeading() {

        String text = "<!--\n## Hidden\n-->\n\n```\n## Code\n```\n\n## Shown\n";

        assertThat(HeadingAnchors.anchors(text)).containsExactly("shown");
    }

    @Test
    void findsWhereTheHeadingNamedByAnAnchorStarts() {

        String text = "# Departure\n\n## Night\n\nText.\n\n## Night\n";

        assertThat(HeadingAnchors.start(text, "night-1")).hasValue(text.lastIndexOf("## Night"));
        assertThat(HeadingAnchors.start(text, "departure")).hasValue(0);
        assertThat(HeadingAnchors.start(text, "day")).isEmpty();
    }
}
