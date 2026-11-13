package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.LinkTarget;
import it.infog.psiche.markdown.model.TextEdit;
import it.infog.psiche.markdown.model.TextLink;
import it.infog.psiche.markdown.model.TextRange;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TextLinksTest {

    private static final String STORM = "[the storm](02-harbour.md#the-storm)";

    // Finding links

    @Test
    void findsEachInlineLinkWithItsRangeTextAndDestination() {

        String text = "# Departure\n\nAnna remembered " + STORM + " and left.\n";

        int start = text.indexOf(STORM);
        assertThat(TextLinks.of(text)).containsExactly(new TextLink(new TextRange(start, start + STORM.length()),
                new TextRange(start + 1, start + "[the storm".length()), "the storm", "02-harbour.md#the-storm", ""));
    }

    @Test
    void findsALinkInATablesCell() {

        String text = "| Chapter | Where |\n|---|---|\n| 2 | " + STORM + " |\n";

        int start = text.indexOf(STORM);
        assertThat(TextLinks.of(text)).containsExactly(new TextLink(new TextRange(start, start + STORM.length()),
                new TextRange(start + 1, start + "[the storm".length()), "the storm", "02-harbour.md#the-storm", ""));
    }

    @Test
    void theLabelKeepsTheTextAsWrittenAndTheTextIsAsTheReaderSeesIt() {

        String text = "See [the *old* \\[harbour\\]](https://example.com \"The port\").";

        assertThat(TextLinks.of(text)).singleElement().satisfies(link -> {
            assertThat(text.substring(link.label().start(), link.label().end()))
                    .isEqualTo("the *old* \\[harbour\\]");
            assertThat(link.text()).isEqualTo("the old [harbour]");
            assertThat(link.title()).isEqualTo("The port");
        });
    }

    @Test
    void theAddressRunsFromTheBracketClosingTheTextToTheClosingParenthesis() {

        String text = "See [the map](Harbour%20office.md#the-clerk \"Clerk\") today.";

        TextRange address = TextLinks.of(text).getFirst().address();
        assertThat(text.substring(address.start(), address.end()))
                .isEqualTo("](Harbour%20office.md#the-clerk \"Clerk\")");
    }

    @Test
    void theMarkersAreTheOpeningBracketAndTheAddress() {

        String text = "See [the *old* map](map.md) and [](empty.md).";

        assertThat(TextLinks.of(text)).satisfiesExactly(
                link -> assertThat(written(text, link.markers())).containsExactly("[", "](map.md)"),
                link -> assertThat(written(text, link.markers())).containsExactly("[", "](empty.md)"));
    }

    @Test
    void linksByReferenceBareAddressesImagesAndCommentsAreNotFound() {

        String text = "A [reference][ref] and <https://example.com> and ![a map](map.png).\n\n"
                + "<!-- [hidden](01.md) -->\n\n[ref]: https://example.com\n";

        assertThat(TextLinks.of(text)).isEmpty();
    }

    @Test
    void aCaretInsideALinkFindsItButOneJustBeforeOrAfterItDoesNot() {

        String text = "Go " + STORM + " now.";
        int start = text.indexOf(STORM);
        int end = start + STORM.length();

        assertThat(TextLinks.at(text, start + 1, start + 1)).isPresent();
        assertThat(TextLinks.at(text, start, end)).isPresent();
        assertThat(TextLinks.at(text, start, start)).isEmpty();
        assertThat(TextLinks.at(text, end, end)).isEmpty();
        assertThat(TextLinks.at(text, 0, end)).isEmpty();
    }

    // Writing links

    @Test
    void writesALinkEscapingItsBracketsAndPuttingADestinationWithSpacesInAngleBrackets() {

        assertThat(TextLinks.markdown(" the [old] harbour ", "02 The harbour.md"))
                .isEqualTo("[the \\[old\\] harbour](<02 The harbour.md>)");
        assertThat(TextLinks.markdown("Psiche", "https://example.com")).isEqualTo("[Psiche](https://example.com)");
    }

    @Test
    void aChapterDestinationNamesTheFileAndTheHeadingOrTheHeadingAloneInTheSameChapter() {

        assertThat(TextLinks.chapterDestination("01-departure", "02-harbour", "")).isEqualTo("02-harbour.md");
        assertThat(TextLinks.chapterDestination("01-departure", "02-harbour", "the-storm"))
                .isEqualTo("02-harbour.md#the-storm");
        assertThat(TextLinks.chapterDestination("02-harbour", "02-harbour", "the-storm")).isEqualTo("#the-storm");
        assertThat(TextLinks.chapterDestination("02-harbour", "02-harbour", "")).isEqualTo("02-harbour.md");
    }

    @Test
    void aChapterNameWithAHashOrPercentIsReadBackAsWritten() {

        String destination = TextLinks.chapterDestination("01", "Track #5 at 100%", "night");

        assertThat(destination).isEqualTo("Track %235 at 100%25.md#night");
        assertThat(TextLinks.target(destination, "01")).isEqualTo(LinkTarget.chapter("Track #5 at 100%", "night"));
    }

    @Test
    void aWebAddressTypedWithoutSchemeGetsHttpsAndAMailAddressGetsMailto() {

        assertThat(TextLinks.webAddress(" www.example.com ")).isEqualTo("https://www.example.com");
        assertThat(TextLinks.webAddress("anna@example.com")).isEqualTo("mailto:anna@example.com");
        assertThat(TextLinks.webAddress("http://example.com")).isEqualTo("http://example.com");
        assertThat(TextLinks.webAddress("  ")).isEmpty();
    }

    // Reading links

    @Test
    void readsWebAndMailAddresses() {

        assertThat(TextLinks.target("https://example.com/a?b=c", "01")).isEqualTo(LinkTarget.WEB);
        assertThat(TextLinks.target("HTTP://example.com", "01")).isEqualTo(LinkTarget.WEB);
        assertThat(TextLinks.target("mailto:anna@example.com", "01")).isEqualTo(LinkTarget.WEB);
    }

    @Test
    void aWebAddressWithoutHostOrWithAnotherSchemeIsMalformed() {

        assertThat(TextLinks.target("https://", "01")).isEqualTo(LinkTarget.MALFORMED);
        assertThat(TextLinks.target("https:example.com", "01")).isEqualTo(LinkTarget.MALFORMED);
        assertThat(TextLinks.target("ftp://example.com", "01")).isEqualTo(LinkTarget.MALFORMED);
        assertThat(TextLinks.target("mailto:", "01")).isEqualTo(LinkTarget.MALFORMED);
        assertThat(TextLinks.target("https://exa mple.com", "01")).isEqualTo(LinkTarget.MALFORMED);
    }

    @Test
    void readsAChapterHoweverItsFileIsWritten() {

        LinkTarget harbour = LinkTarget.chapter("02 The harbour", "");

        assertThat(TextLinks.target("02 The harbour.md", "01")).isEqualTo(harbour);
        assertThat(TextLinks.target("02%20The%20harbour.md", "01")).isEqualTo(harbour);
        assertThat(TextLinks.target("./02 The harbour.md", "01")).isEqualTo(harbour);
        assertThat(TextLinks.target("../chapters/02 The harbour.md", "01")).isEqualTo(harbour);
        assertThat(TextLinks.target("C++ notes.md", "01")).isEqualTo(LinkTarget.chapter("C++ notes", ""));
    }

    @Test
    void readsAHeadingOfAnotherChapterOrOfItsOwn() {

        assertThat(TextLinks.target("02-harbour.md#the-storm", "01"))
                .isEqualTo(LinkTarget.chapter("02-harbour", "the-storm"));
        assertThat(TextLinks.target("#the-storm", "01")).isEqualTo(LinkTarget.chapter("01", "the-storm"));
    }

    @Test
    void aFileThatIsNoChapterIsMalformed() {

        assertThat(TextLinks.target("../media/01/map.png", "01")).isEqualTo(LinkTarget.MALFORMED);
        assertThat(TextLinks.target("../README.md", "01")).isEqualTo(LinkTarget.MALFORMED);
        assertThat(TextLinks.target("drafts/02.md", "01")).isEqualTo(LinkTarget.MALFORMED);
        assertThat(TextLinks.target(".md", "01")).isEqualTo(LinkTarget.MALFORMED);
        assertThat(TextLinks.target("", "01")).isEqualTo(LinkTarget.MALFORMED);
        assertThat(TextLinks.target("02%zz.md", "01")).isEqualTo(LinkTarget.MALFORMED);
    }

    @Test
    void brokenLinksLeadToAMissingChapterOrHeadingOrAMalformedAddress() {

        String text = "# Departure\n\n## Night\n\n"
                + "[ok 1](02-harbour.md) [ok 2](02-harbour.md#the-storm) [ok 3](#night) [ok 4](https://example.com)\n"
                + "[no chapter](03-gone.md) [no heading](02-harbour.md#the-calm) [no own heading](#day) "
                + "[malformed](ftp://example.com)\n";
        Map<String, Set<String>> anchors = Map.of("01-departure", Set.of(), "02-harbour", Set.of("the-storm"));

        assertThat(TextLinks.broken(text, TextLinks.of(text), "01-departure", anchors)).extracting(TextLink::text)
                .containsExactly("no chapter", "no heading", "no own heading", "malformed");
    }

    // Changing links

    @Test
    void removingALinkKeepsItsTextAsWrittenAndSelectsIt() {

        String text = "Go [the *old* harbour](02.md) now.";
        TextLink link = TextLinks.of(text).getFirst();

        TextEdit edit = TextLinks.unlinked(text, link);

        assertThat(apply(text, edit)).isEqualTo("Go the *old* harbour now.");
        assertThat(edit.selectionStart()).isEqualTo(3);
        assertThat(edit.selectionEnd()).isEqualTo(3 + "the *old* harbour".length());
    }

    @Test
    void aRenamedChapterIsFollowedKeepingTheTextHeadingAndTitle() {

        String text = "See [the *storm*](02-harbour.md#the-storm \"Storm\"), [the port](./02-harbour.md), "
                + "[another](03-sea.md), [here](#night) and [the web](https://example.com).\n";

        assertThat(TextLinks.withChapter(text, "02-harbour", "02 The harbour")).isEqualTo(
                "See [the *storm*](<02 The harbour.md#the-storm> \"Storm\"), [the port](<02 The harbour.md>), "
                        + "[another](03-sea.md), [here](#night) and [the web](https://example.com).\n");
    }

    @Test
    void imageLinesAreWrittenAsBeforeWithTheSharedEscaping() {

        assertThat(ImageLines.markdown("The [old] harbour", "../media/01 (draft)/map.png"))
                .isEqualTo("![The \\[old\\] harbour](<../media/01 (draft)/map.png>)");
    }

    private static String apply(String text, TextEdit edit) {

        return text.substring(0, edit.start()) + edit.replacement() + text.substring(edit.end());
    }

    private static List<String> written(String text, List<TextRange> ranges) {

        return ranges.stream().map(range -> text.substring(range.start(), range.end())).toList();
    }
}
