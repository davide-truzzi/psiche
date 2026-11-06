package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.ImageLine;
import it.infog.psiche.markdown.model.TextEdit;
import it.infog.psiche.markdown.model.TextRange;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageLinesTest {

    private static final String MAP = "![Map of the coast](../media/01-departure/map.png)";

    @Test
    void findsEachParagraphThatIsAnImageAloneWithItsRangeCaptionAndDestination() {

        String text = "# Departure\n\nAnna kept the office.\n\n" + MAP + "\n\nThe end.\n";

        int start = text.indexOf(MAP);
        assertThat(ImageLines.of(text)).containsExactly(new ImageLine(new TextRange(start, start + MAP.length()),
                "Map of the coast", "../media/01-departure/map.png"));
    }

    @Test
    void anImageInsideASentenceOrACommentIsNoImageLine() {

        String text = "She looked at ![the map](map.png) and sighed.\n\n<!--\n" + MAP + "\n-->\n";

        assertThat(ImageLines.of(text)).isEmpty();
    }

    @Test
    void theCaptionIsReadAsTheReaderSeesIt() {

        String text = ImageLines.markdown("The [old] harbour \\ dawn", "../media/03-harbour/old harbour.jpg");

        assertThat(ImageLines.of(text)).singleElement().satisfies(line -> {
            assertThat(line.caption()).isEqualTo("The [old] harbour \\ dawn");
            assertThat(line.destination()).isEqualTo("../media/03-harbour/old harbour.jpg");
        });
    }

    @Test
    void aDestinationWithSpacesOrParenthesesGoesInAngleBrackets() {

        assertThat(ImageLines.markdown("Map", ImageLines.destination("01 (draft)", "map.png")))
                .isEqualTo("![Map](<../media/01 (draft)/map.png>)");
        assertThat(ImageLines.markdown(" Map ", ImageLines.destination("01-departure", "map.png")))
                .isEqualTo(MAP.replace("Map of the coast", "Map"));
    }

    @Test
    void anImageInsertedMidTextGetsABlankLineBeforeAndAfter() {

        String text = "First paragraph.\nSecond paragraph.";

        TextEdit edit = ImageLines.insert(text, "First paragraph.\n".length(), MAP);

        assertThat(apply(text, edit)).isEqualTo("First paragraph.\n\n" + MAP + "\n\nSecond paragraph.");
        assertThat(edit.selectionStart()).isEqualTo(edit.selectionEnd())
                .isEqualTo("First paragraph.\n\n".length() + MAP.length());
    }

    @Test
    void blankLinesTheTextHasAlreadyAreNotDoubled() {

        String text = "First.\n\nSecond.";

        assertThat(apply(text, ImageLines.insert(text, "First.\n\n".length(), MAP)))
                .isEqualTo("First.\n\n" + MAP + "\n\nSecond.");
    }

    @Test
    void atTheStartNothingComesBeforeAndAtTheEndOneLineEndFollows() {

        assertThat(apply("Text.", ImageLines.insert("Text.", 0, MAP))).isEqualTo(MAP + "\n\nText.");
        assertThat(apply("Text.", ImageLines.insert("Text.", 5, MAP))).isEqualTo("Text.\n\n" + MAP + "\n");
        assertThat(apply("", ImageLines.insert("", 0, MAP))).isEqualTo(MAP + "\n");
    }

    @Test
    void aCaretInsideALinePutsTheImageAfterThatLine() {

        String text = "Anna wrote the date.\nAgain.\n\nNext.";

        assertThat(apply(text, ImageLines.insert(text, "Anna wrote".length(), MAP)))
                .isEqualTo("Anna wrote the date.\n\n" + MAP + "\n\nAgain.\n\nNext.");
        assertThat(apply("Last line", ImageLines.insert("Last line", 4, MAP))).isEqualTo("Last line\n\n" + MAP + "\n");
    }

    private static String apply(String text, TextEdit edit) {

        return text.substring(0, edit.start()) + edit.replacement() + text.substring(edit.end());
    }
}
