package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextEdit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownFormatterTest {

    @Test
    void boldWrapsTheSelectionAndSelectsTheResult() {
        String text = "It was very dark.";

        TextEdit edit = MarkdownFormatter.bold(text, 7, 11);

        assertThat(apply(text, edit)).isEqualTo("It was **very** dark.");
        assertThat(selected(text, edit)).isEqualTo("**very**");
    }

    @Test
    void boldKeepsSpacesAtTheEdgesOutsideTheMarkers() {
        String text = "It was very dark.";

        assertThat(apply(text, MarkdownFormatter.bold(text, 6, 12))).isEqualTo("It was **very** dark.");
    }

    @Test
    void italicOnBoldTextAddsToIt() {
        String text = "**very**";

        assertThat(apply(text, MarkdownFormatter.italic(text, 0, 8))).isEqualTo("***very***");
    }

    @Test
    void italicOnBoldTextInATablesCellAddsToIt() {
        String text = "| Grassetto | Risultato |\n|---|---|\n| Entrambi | **parola** |";
        int start = text.indexOf("**parola**");

        assertThat(apply(text, MarkdownFormatter.italic(text, start, start + "**parola**".length())))
                .isEqualTo("| Grassetto | Risultato |\n|---|---|\n| Entrambi | ***parola*** |");
    }

    @Test
    void boldWrapsEachLineAndLeavesEmptyLinesAndLineMarkersOutside() {
        String text = "# Storm\n\n- north\n- south";

        assertThat(apply(text, MarkdownFormatter.bold(text, 0, text.length())))
                .isEqualTo("# **Storm**\n\n- **north**\n- **south**");
    }

    @Test
    void boldJoinsTheBoldTextTheSelectionOverlaps() {
        String text = "**lorem** ipsum";

        // "rem** ip" is selected.
        TextEdit edit = MarkdownFormatter.bold(text, 4, 12);

        assertThat(apply(text, edit)).isEqualTo("**lorem ip**sum");
        assertThat(selected(text, edit)).isEqualTo("**lorem ip**");
    }

    @Test
    void boldJoinsBoldTextItTouches() {
        String text = "**lorem**ipsum";

        assertThat(apply(text, MarkdownFormatter.bold(text, 9, 14))).isEqualTo("**loremipsum**");
    }

    @Test
    void boldJoinsEveryBoldPartOfTheSelection() {
        String text = "**one** two __three__";

        assertThat(apply(text, MarkdownFormatter.bold(text, 0, text.length()))).isEqualTo("**one two three**");
    }

    @Test
    void boldDoesNothingToTextThatIsAlreadyBold() {
        String text = "It was **very dark** and cold.";

        assertThat(apply(text, MarkdownFormatter.bold(text, 13, 17))).isEqualTo(text);
        assertThat(apply(text, MarkdownFormatter.bold(text, 7, 19))).isEqualTo(text);
    }

    @Test
    void italicDoesNothingToTextThatIsAlreadyItalicInsideBold() {
        String text = "***very***";

        assertThat(apply(text, MarkdownFormatter.italic(text, 3, 7))).isEqualTo(text);
    }

    @Test
    void boldDoesNothingWithoutASelection() {
        String text = "dark";

        assertThat(apply(text, MarkdownFormatter.bold(text, 2, 2))).isEqualTo(text);
    }

    @Test
    void strikethroughWrapsEachLineAndJoinsTheStrikethroughItTouches() {
        assertThat(MarkdownFormatter.strikethrough("one two", 0, 7).replacement()).isEqualTo("~~one two~~");
        assertThat(MarkdownFormatter.strikethrough("~~one~~ two", 4, 11).replacement()).isEqualTo("~~one two~~");
    }

    @Test
    void strikethroughDoesNotTakeASubscriptForItself() {
        TextEdit edit = MarkdownFormatter.strikethrough("H~2~O and", 0, 9);

        assertThat(edit.replacement()).isEqualTo("~~H~2~O and~~");
    }

    @Test
    void subscriptAndSuperscriptWrapTheSelectionInOneSign() {
        assertThat(MarkdownFormatter.subscript("H2O", 1, 2).replacement()).isEqualTo("~2~");
        assertThat(MarkdownFormatter.superscript("mc2", 2, 3).replacement()).isEqualTo("^2^");
    }

    @Test
    void subscriptInsideAStrikethroughKeepsBoth() {
        TextEdit edit = MarkdownFormatter.subscript("~~H2O~~", 3, 4);

        assertThat(edit.replacement()).isEqualTo("~2~");
        assertThat(edit.start()).isEqualTo(3);
    }

    @Test
    void onlyTextWithoutSpacesFitsAScript() {
        assertThat(MarkdownFormatter.fitsScript("H2O", 1, 2)).isTrue();
        assertThat(MarkdownFormatter.fitsScript("a b", 0, 3)).isFalse();
        assertThat(MarkdownFormatter.fitsScript("a\nb", 0, 3)).isFalse();
        assertThat(MarkdownFormatter.fitsScript("ab", 1, 1)).isFalse();
    }

    @Test
    void clearFormattingRemovesStrikethroughSubscriptAndSuperscript() {
        TextEdit edit = MarkdownFormatter.clearFormatting("~~gone~~ H~2~O x^2^", 0, 19);

        assertThat(edit.replacement()).isEqualTo("gone H2O x2");
    }

    @Test
    void clearFormattingKeepsTildesAndCaretsThatFormatNothing() {
        TextEdit edit = MarkdownFormatter.clearFormatting("about ~30, a ^ b", 0, 16);

        assertThat(edit.replacement()).isEqualTo("about ~30, a ^ b");
    }

    @Test
    void headingAppliesToTheCaretLineAndReplacesAnotherLevel() {
        String text = "Intro\n## The storm\nText";

        TextEdit edit = MarkdownFormatter.heading(text, 9, 9, 1);

        assertThat(apply(text, edit)).isEqualTo("Intro\n# The storm\nText");
        assertThat(selected(text, edit)).isEqualTo("# The storm");
    }

    @Test
    void headingReplacesAListMarkerAndKeepsAQuote() {
        String text = "> - north";

        assertThat(apply(text, MarkdownFormatter.heading(text, 0, 0, 3))).isEqualTo("> ### north");
    }

    @Test
    void spansOneLineIgnoresEmptyLinesAndALineEndingTheSelection() {
        String text = "one\n\ntwo\nthree";

        assertThat(MarkdownFormatter.spansOneLine(text, 0, 5)).isTrue();
        assertThat(MarkdownFormatter.spansOneLine(text, 5, 9)).isTrue();
        assertThat(MarkdownFormatter.spansOneLine(text, 0, 6)).isFalse();
    }

    @Test
    void bulletedListMarksEveryNonEmptyLineTouched() {
        String text = "Before\nnorth\n\nsouth\nAfter";

        // From the middle of "north" to the middle of "south".
        assertThat(apply(text, MarkdownFormatter.bulletedList(text, 9, 16)))
                .isEqualTo("Before\n- north\n\n- south\nAfter");
    }

    @Test
    void aSelectionEndingAtTheStartOfALineLeavesThatLineOut() {
        String text = "north\nsouth";

        assertThat(apply(text, MarkdownFormatter.bulletedList(text, 0, 6))).isEqualTo("- north\nsouth");
    }

    @Test
    void numberedListNumbersFromOneAndReplacesBullets() {
        String text = "- north\n- south\n\n- east";

        assertThat(apply(text, MarkdownFormatter.numberedList(text, 0, text.length())))
                .isEqualTo("1. north\n2. south\n\n3. east");
    }

    @Test
    void listsLeaveSceneBreaksAlone() {
        String text = "north\n* * *\nsouth";

        assertThat(apply(text, MarkdownFormatter.bulletedList(text, 0, text.length())))
                .isEqualTo("- north\n* * *\n- south");
    }

    @Test
    void quoteMarksEveryLineSoParagraphsStayInOneQuote() {
        String text = "Run!\n\nNow.";

        assertThat(apply(text, MarkdownFormatter.quote(text, 0, text.length()))).isEqualTo("> Run!\n>\n> Now.");
    }

    @Test
    void quotingAQuoteNestsIt() {
        String text = "> Run!";

        assertThat(apply(text, MarkdownFormatter.quote(text, 0, 0))).isEqualTo("> > Run!");
    }

    @Test
    void clearFormattingRemovesLineMarkersAndEmphasis() {
        String text = "> ## The **storm**\n- It was *very* dark.\n2. And __cold__.";

        TextEdit edit = MarkdownFormatter.clearFormatting(text, 0, text.length());

        assertThat(apply(text, edit)).isEqualTo("The storm\nIt was very dark.\nAnd cold.");
        assertThat(selected(text, edit)).isEqualTo("The storm\nIt was very dark.\nAnd cold.");
    }

    @Test
    void clearFormattingRemovesTheWholeEmphasisTheSelectionOverlaps() {
        String text = "It was **very dark** and cold.";

        // Only "dark" is selected.
        assertThat(apply(text, MarkdownFormatter.clearFormatting(text, 14, 18))).isEqualTo("It was very dark and cold.");
    }

    @Test
    void clearFormattingKeepsLiteralAsterisksEscapesAndSceneBreaks() {
        String text = "2 * 3 * 4 is \\*not\\* italic\n***\n- - -";

        assertThat(apply(text, MarkdownFormatter.clearFormatting(text, 0, text.length()))).isEqualTo(text);
    }

    @Test
    void clearFormattingLeavesEmphasisOutsideTheSelection() {
        String text = "*one* two *three*";

        assertThat(apply(text, MarkdownFormatter.clearFormatting(text, 6, 9))).isEqualTo(text);
    }

    @Test
    void clearFormattingRemovesBoldItalicTogether() {
        String text = "***very***";

        assertThat(apply(text, MarkdownFormatter.clearFormatting(text, 3, 7))).isEqualTo("very");
    }

    @Test
    void commentWrapsTheSelectionAndKeepsItSelected() {
        String text = "It was very dark.";

        TextEdit edit = MarkdownFormatter.comment(text, 7, 11);

        assertThat(apply(text, edit)).isEqualTo("It was <!-- very --> dark.");
        assertThat(selected(text, edit)).isEqualTo("very");
    }

    @Test
    void commentOnTheSameSelectionMakesItTextAgain() {
        String text = "It was very dark.";
        TextEdit edit = MarkdownFormatter.comment(text, 7, 11);
        String commented = apply(text, edit);

        TextEdit undone = MarkdownFormatter.comment(commented, edit.selectionStart(), edit.selectionEnd());

        assertThat(apply(commented, undone)).isEqualTo(text);
        assertThat(selected(commented, undone)).isEqualTo("very");
    }

    @Test
    void commentWithTheCaretInACommentUncommentsIt() {
        String text = "Rain. <!-- Check the date of the flood. --> Then wind.";

        assertThat(apply(text, MarkdownFormatter.comment(text, 20, 20))).isEqualTo(
                "Rain. Check the date of the flood. Then wind.");
    }

    @Test
    void commentWithNothingSelectedCommentsTheCaretsLine() {
        String text = "The storm.\nIt was very dark.\nAnd cold.";

        assertThat(apply(text, MarkdownFormatter.comment(text, 14, 14)))
                .isEqualTo("The storm.\n<!-- It was very dark. -->\nAnd cold.");
    }

    @Test
    void commentOnAnEmptyLinePutsAnEmptyCommentWithTheCaretInside() {
        String text = "Rain.\n\nWind.";

        TextEdit edit = MarkdownFormatter.comment(text, 6, 6);

        assertThat(apply(text, edit)).isEqualTo("Rain.\n<!--  -->\nWind.");
        assertThat(edit.selectionStart()).isEqualTo(11).isEqualTo(edit.selectionEnd());
    }

    @Test
    void commentOverSeveralLinesTakesThemWholeSoItMayRunOverBlankLines() {
        String text = "It was very dark.\n\nAnd cold.";

        // "dark.\n\nAnd" is selected.
        assertThat(apply(text, MarkdownFormatter.comment(text, 12, 22)))
                .isEqualTo("<!-- It was very dark.\n\nAnd cold. -->");
    }

    @Test
    void commentJoinsTheCommentsInsideTheSelection() {
        String text = "Rain <!-- or hail? --> and wind.";

        assertThat(apply(text, MarkdownFormatter.comment(text, 0, text.length())))
                .isEqualTo("<!-- Rain or hail? and wind. -->");
    }

    @Test
    void commentLeftOpenRunsToTheEndAndIsUncommentedWhole() {
        String text = "Rain. <!-- Wind, and";

        assertThat(apply(text, MarkdownFormatter.comment(text, 18, 18))).isEqualTo("Rain. Wind, and");
    }

    @Test
    void commentAfterAClosedCommentDoesNotTakeItForTheOneAroundTheCaret() {
        String text = "<!-- Rain. --> Wind.";

        assertThat(apply(text, MarkdownFormatter.comment(text, 16, 16))).isEqualTo("<!-- Rain. Wind. -->");
    }

    private static String apply(String text, TextEdit edit) {
        return text.substring(0, edit.start()) + edit.replacement() + text.substring(edit.end());
    }

    private static String selected(String text, TextEdit edit) {
        return apply(text, edit).substring(edit.selectionStart(), edit.selectionEnd());
    }
}
