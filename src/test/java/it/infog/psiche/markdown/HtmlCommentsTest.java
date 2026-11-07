package it.infog.psiche.markdown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class HtmlCommentsTest {

    @ParameterizedTest
    @MethodSource("chapters")
    void findsTheCommentsOfAChapter(String markdown, List<String> expected) {

        assertThat(comments(markdown)).containsExactlyElementsOf(expected);
    }

    @Test
    void aClosedCommentHasItsOpeningAndClosingAsMarkersAndAnOpenOneItsOpeningAlone() {

        assertThat(markers("One <!-- a note --> two.")).containsExactly("<!--", "-->");
        assertThat(markers("<!---->\n")).containsExactly("<!--", "-->");
        assertThat(markers("Before.\n\n<!-- left open\n")).containsExactly("<!--");
        assertThat(markers("Before.\n\n<!-->\n")).containsExactly("<!--");
    }

    @Test
    void stripsCommentsFromHtml() {

        assertThat(HtmlComments.strip("<p>Kept<!-- gone --> too</p><!-- open")).isEqualTo("<p>Kept too</p>");
    }

    private static Stream<Arguments> chapters() {

        return Stream.of(
                argumentSet("a comment inside a paragraph",
                        "It began at dawn, <!-- check the hour --> when the carts left.",
                        List.of("<!-- check the hour -->")),
                argumentSet("two comments in one paragraph", "One <!-- a --> and two <!-- b -->.",
                        List.of("<!-- a -->", "<!-- b -->")),
                argumentSet("a comment over several lines on its own",
                        "# Alba\n\n<!--\nA comment over\nthree lines\n-->\nMarco lied.\n",
                        List.of("<!--\nA comment over\nthree lines\n-->")),
                argumentSet("a comment that ends a paragraph over several lines",
                        "Marco lied.<!-- he read\nit the night before -->\n",
                        List.of("<!-- he read\nit the night before -->")),
                argumentSet("a comment left open on its own line runs to the end",
                        "Before.\n\n<!-- left open\n\nstill hidden\n", List.of("<!-- left open\n\nstill hidden")),
                argumentSet("a comment left open inside a paragraph is text", "It began <!-- left open\n", List.of()),
                argumentSet("a comment started mid-line does not run past a blank line",
                        "It began <!-- a note\n\nthat goes on --> here.\n", List.of()),
                argumentSet("a comment in code is text",
                        "Write `<!-- this -->` for a note.\n\n```\n<!-- not one either -->\n```\n", List.of()),
                argumentSet("a comment in a quote", "> She said <!-- who? --> no.\n", List.of("<!-- who? -->")),
                argumentSet("other HTML is no comment", "A <span>word</span>.\n\n<div>A block</div>\n",
                        List.of()));
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
