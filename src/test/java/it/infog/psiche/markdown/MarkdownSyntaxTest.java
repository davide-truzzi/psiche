package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.Subscript;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomNode;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownSyntaxTest {

    @Test
    void drawsTwoTildesAsAStrikethrough() {
        assertThat(html("It was ~~not~~ dark.")).isEqualTo("<p>It was <del>not</del> dark.</p>\n");
    }

    @Test
    void letsAStrikethroughHoldSpaces() {
        assertThat(html("~~no longer true~~")).isEqualTo("<p><del>no longer true</del></p>\n");
    }

    @Test
    void drawsOneTildeEachSideAsASubscript() {
        assertThat(html("H~2~O")).isEqualTo("<p>H<sub>2</sub>O</p>\n");
    }

    @Test
    void drawsOneCaretEachSideAsASuperscript() {
        assertThat(html("E = mc^2^, the 1^st^")).isEqualTo("<p>E = mc<sup>2</sup>, the 1<sup>st</sup></p>\n");
    }

    @Test
    void leavesSignsWithASpaceBetweenThemAsTheyAreWritten() {
        assertThat(html("About ~30 or ~40 people, a ^b ^c.")).isEqualTo("<p>About ~30 or ~40 people, a ^b ^c.</p>\n");
        assertThat(html("~a b~ and ^a b^")).isEqualTo("<p>~a b~ and ^a b^</p>\n");
    }

    @Test
    void leavesSignsWithALineBreakBetweenThemAsTheyAreWritten() {
        assertThat(html("~one\ntwo~")).isEqualTo("<p>~one\ntwo~</p>\n");
    }

    @Test
    void leavesASignAloneOrAnEmptyPairAsItIsWritten() {
        assertThat(html("~ 30 people, 2 ^ 3")).isEqualTo("<p>~ 30 people, 2 ^ 3</p>\n");
        assertThat(html("^^ and ~~")).isEqualTo("<p>^^ and ~~</p>\n");
    }

    @Test
    void keepsEscapedSignsAsSigns() {
        assertThat(html("H\\~2\\~O and X\\^2\\^")).isEqualTo("<p>H~2~O and X^2^</p>\n");
    }

    @Test
    void putsASubscriptInsideAStrikethrough() {
        assertThat(html("~~H~2~O~~")).isEqualTo("<p><del>H<sub>2</sub>O</del></p>\n");
    }

    @Test
    void placesAScriptInTheTextFromItsOpeningToItsClosingSign() {
        String text = "H~2~O";
        List<SourceSpan> spans = new ArrayList<>();
        MarkdownSyntax.parser(IncludeSourceSpans.BLOCKS_AND_INLINES).parse(text).accept(new AbstractVisitor() {
            @Override
            public void visit(CustomNode node) {
                if (node instanceof Subscript)
                    spans.addAll(node.getSourceSpans());
                visitChildren(node);
            }
        });

        assertThat(spans.getFirst().getInputIndex()).isEqualTo(1);
        assertThat(spans.getLast().getInputIndex() + spans.getLast().getLength()).isEqualTo(4);
    }

    private static String html(String markdown) {
        return MarkdownSyntax.htmlRenderer().build().render(MarkdownSyntax.parser().parse(markdown));
    }
}
