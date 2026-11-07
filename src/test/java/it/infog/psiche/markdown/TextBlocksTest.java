package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextLink;
import it.infog.psiche.markdown.model.TextRange;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextBlocksTest {

    // "See [the map](map.md) today.\n\n![Dusk](dusk.png)\n": a link from 4 to 21, an image line from 30 to 48.
    private static final TextRange LINK = new TextRange(4, 21);
    private static final TextRange IMAGE = new TextRange(30, 48);
    private static final List<TextRange> BLOCKS = List.of(LINK, IMAGE);
    // The link's text, "the map", from 5 to 12.
    private static final List<TextLink> LINKS =
            List.of(new TextLink(LINK, new TextRange(5, 12), "the map", "map.md", ""));
    private static final List<TextRange> IMAGES = List.of(IMAGE);

    @Test
    void typingInsideABlockIsRefused() {

        assertThat(TextBlocks.edit(BLOCKS, 10, 10)).isEmpty();
        assertThat(TextBlocks.edit(BLOCKS, 47, 47)).isEmpty();
    }

    @Test
    void typingRightBeforeOrAfterABlockOrInTheProseGoesAsItIs() {

        assertThat(TextBlocks.edit(BLOCKS, 4, 4)).contains(new TextRange(4, 4));
        assertThat(TextBlocks.edit(BLOCKS, 21, 21)).contains(new TextRange(21, 21));
        assertThat(TextBlocks.edit(BLOCKS, 0, 3)).contains(new TextRange(0, 3));
    }

    @Test
    void backspaceAfterABlockOrDeleteBeforeOneTakesTheWholeBlock() {

        assertThat(TextBlocks.edit(BLOCKS, 20, 21)).contains(LINK);
        assertThat(TextBlocks.edit(BLOCKS, 30, 31)).contains(IMAGE);
    }

    @Test
    void aSelectionOverPartOfABlockWidensToTheWholeOfIt() {

        assertThat(TextBlocks.edit(BLOCKS, 0, 8)).contains(new TextRange(0, 21));
        assertThat(TextBlocks.edit(BLOCKS, 9, 12)).contains(LINK);
        assertThat(TextBlocks.edit(BLOCKS, 15, 35)).contains(new TextRange(4, 48));
    }

    @Test
    void aSelectionOfWholeBlocksGoesAsItIs() {

        assertThat(TextBlocks.edit(BLOCKS, 4, 21)).contains(LINK);
        assertThat(TextBlocks.edit(BLOCKS, 0, 48)).contains(new TextRange(0, 48));
    }

    @Test
    void aCaretInsideABlockGoesToTheEdgeItMovesTowards() {

        assertThat(TextBlocks.caret(BLOCKS, 5, true)).isEqualTo(21);
        assertThat(TextBlocks.caret(BLOCKS, 20, false)).isEqualTo(4);
    }

    @Test
    void aCaretOutsideABlockOrAtItsEdgeStays() {

        assertThat(TextBlocks.caret(BLOCKS, 2, true)).isEqualTo(2);
        assertThat(TextBlocks.caret(BLOCKS, 4, true)).isEqualTo(4);
        assertThat(TextBlocks.caret(BLOCKS, 21, false)).isEqualTo(21);
        assertThat(TextBlocks.nearestEdge(BLOCKS, 25)).isEqualTo(25);
    }

    @Test
    void aClickInsideABlockPutsTheCaretAtTheNearerEdge() {

        assertThat(TextBlocks.nearestEdge(BLOCKS, 8)).isEqualTo(4);
        assertThat(TextBlocks.nearestEdge(BLOCKS, 17)).isEqualTo(21);
    }

    @Test
    void nothingTouchingAnImageLineCanBeFormattedNotEvenItsCaption() {

        assertThat(TextBlocks.formattable(IMAGES, LINKS, 32, 36)).isFalse();
        assertThat(TextBlocks.formattable(IMAGES, LINKS, 22, 35)).isFalse();
        assertThat(TextBlocks.formattable(IMAGES, LINKS, 30, 48)).isFalse();
        assertThat(TextBlocks.formattable(IMAGES, LINKS, 48, 48)).isFalse();
    }

    @Test
    void aLinksTextOrAStretchCoveringWholeLinksCanBeFormatted() {

        assertThat(TextBlocks.formattable(IMAGES, LINKS, 9, 12)).isTrue();
        assertThat(TextBlocks.formattable(IMAGES, LINKS, 0, 27)).isTrue();
        assertThat(TextBlocks.formattable(IMAGES, LINKS, 22, 27)).isTrue();
    }

    @Test
    void aSelectionCuttingAcrossALinksEdgeOrIntoItsAddressCannotBeFormatted() {

        assertThat(TextBlocks.formattable(IMAGES, LINKS, 0, 8)).isFalse();
        assertThat(TextBlocks.formattable(IMAGES, LINKS, 9, 16)).isFalse();
        assertThat(TextBlocks.formattable(IMAGES, LINKS, 15, 15)).isFalse();
    }
}
