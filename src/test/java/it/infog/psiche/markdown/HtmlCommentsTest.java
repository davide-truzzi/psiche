package it.infog.psiche.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlCommentsTest {

    @Test
    void findsACommentInsideAParagraph() {

        assertThat(comments("It began at dawn, <!-- check the hour --> when the carts left."))
                .containsExactly("<!-- check the hour -->");
    }

    @Test
    void findsTwoCommentsInOneParagraph() {

        assertThat(comments("One <!-- a --> and two <!-- b -->."))
                .containsExactly("<!-- a -->", "<!-- b -->");
    }

    @Test
    void findsACommentOverSeveralLinesOnItsOwn() {

        assertThat(comments("# Alba\n\n<!--\nA comment over\nthree lines\n-->\nMarco lied.\n"))
                .containsExactly("<!--\nA comment over\nthree lines\n-->");
    }

    @Test
    void findsACommentThatEndsAParagraphOverSeveralLines() {

        assertThat(comments("Marco lied.<!-- he read\nit the night before -->\n"))
                .containsExactly("<!-- he read\nit the night before -->");
    }

    @Test
    void aCommentLeftOpenOnItsOwnLineRunsToTheEnd() {

        assertThat(comments("Before.\n\n<!-- left open\n\nstill hidden\n"))
                .containsExactly("<!-- left open\n\nstill hidden");
    }

    @Test
    void aCommentLeftOpenInsideAParagraphIsText() {

        assertThat(comments("It began <!-- left open\n")).isEmpty();
    }

    @Test
    void aCommentStartedMidLineDoesNotRunPastABlankLine() {

        assertThat(comments("It began <!-- a note\n\nthat goes on --> here.\n")).isEmpty();
    }

    @Test
    void aClosedCommentHasItsOpeningAndClosingAsMarkersAndAnOpenOneItsOpeningAlone() {

        assertThat(markers("One <!-- a note --> two.")).containsExactly("<!--", "-->");
        assertThat(markers("<!---->\n")).containsExactly("<!--", "-->");
        assertThat(markers("Before.\n\n<!-- left open\n")).containsExactly("<!--");
        assertThat(markers("Before.\n\n<!-->\n")).containsExactly("<!--");
    }

    @Test
    void aCommentInCodeIsText() {

        assertThat(comments("Write `<!-- this -->` for a note.\n\n```\n<!-- not one either -->\n```\n")).isEmpty();
    }

    @Test
    void findsACommentInAQuote() {

        assertThat(comments("> She said <!-- who? --> no.\n")).containsExactly("<!-- who? -->");
    }

    @Test
    void otherHtmlIsNoComment() {

        assertThat(comments("A <span>word</span>.\n\n<div>A block</div>\n")).isEmpty();
    }

    @Test
    void stripsCommentsFromHtml() {

        assertThat(HtmlComments.strip("<p>Kept<!-- gone --> too</p><!-- open")).isEqualTo("<p>Kept too</p>");
    }

    /** The text of each comment's markers found in {@code markdown}. */
    private static List<String> markers(String markdown) {

        return HtmlComments.ranges(markdown).stream()
                .flatMap(comment -> HtmlComments.markers(markdown, comment).stream())
                .map(marker -> markdown.substring(marker.start(), marker.end()))
                .toList();
    }

    /** The text of each comment found in {@code markdown}. */
    private static List<String> comments(String markdown) {

        return HtmlComments.ranges(markdown).stream()
                .map(range -> markdown.substring(range.start(), range.end()))
                .toList();
    }
}
