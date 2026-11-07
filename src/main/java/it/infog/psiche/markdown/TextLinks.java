package it.infog.psiche.markdown;

import it.infog.psiche.common.ChapterFolders;
import it.infog.psiche.markdown.model.LinkTarget;
import it.infog.psiche.markdown.model.TextEdit;
import it.infog.psiche.markdown.model.TextLink;
import it.infog.psiche.markdown.model.TextRange;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The links of a chapter, written inline, {@code [text](destination)}: finding them in the text, writing a new one,
 * reading where one leads, and changing or removing them. A link leads to a web or mail address, or to a chapter of
 * the book, {@code 02-harbour.md}, or to a heading in one, {@code 02-harbour.md#the-storm}, or in the same chapter,
 * {@code #the-storm}; destinations are written from the chapter's file, in {@code chapters/}, as GitHub reads them.
 * Links written otherwise, by reference or as a bare {@code <address>}, are left to Markdown as they are.
 */
public final class TextLinks {

    // Characters a link's text escapes, so they stay text: the brackets that would end it, and the backslash itself.
    private static final Pattern LABEL_SPECIAL = Pattern.compile("[\\\\\\[\\]]");
    // Characters that make a destination need angle brackets around it.
    private static final Pattern DESTINATION_SPECIAL = Pattern.compile("[\\s()]");
    // Characters a title escapes, so it stays in its quotes.
    private static final Pattern TITLE_SPECIAL = Pattern.compile("[\\\\\"]");
    // Characters of a chapter's name a destination writes with a percent sign, so they are not read as its anchor.
    private static final Pattern NAME_SPECIAL = Pattern.compile("[%#]");
    // Where a destination is read from: a stand-in for the book's chapters folder, which no file system is asked about.
    private static final Path CHAPTERS = Path.of("chapters");
    // How every inline link's destination starts, after its text: a text without it has none, and need not be parsed.
    private static final String LINK_MIDDLE = "](";
    private static final String ANCHOR_MARK = "#";
    private static final String HTTPS = "https://";
    private static final String MAILTO = "mailto:";

    private static final Parser PARSER =
            Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();

    private TextLinks() {
    }

    // Finding links

    /** The links of {@code text} written inline, in order; those inside an image's caption are part of the image. */
    public static List<TextLink> of(String text) {

        if (!text.contains(LINK_MIDDLE))
            return List.of();

        List<TextLink> links = new ArrayList<>();
        PARSER.parse(text).accept(new AbstractVisitor() {
            @Override
            public void visit(Link link) {

                TextRange range = range(link);
                if (isInline(text, range))
                    links.add(new TextLink(range, label(link, range), PlainText.of(link), link.getDestination(),
                            link.getTitle() == null ? "" : link.getTitle()));
            }

            @Override
            public void visit(Image image) {
            }
        });

        return links;
    }

    /**
     * The link the selection from {@code start} to {@code end} lies in, as Modify link… changes it; a caret just
     * before or after a link is not in it.
     */
    public static Optional<TextLink> at(String text, int start, int end) {

        return of(text).stream()
                .filter(link -> link.range().start() <= start && end <= link.range().end())
                .filter(link -> start < end || (link.range().start() < start && end < link.range().end()))
                .findFirst();
    }

    // Writing links

    /** The Markdown of a link reading {@code text}, which leads to {@code destination}. */
    public static String markdown(String text, String destination) {

        return "[" + escape(LABEL_SPECIAL, text.strip()) + "](" + written(destination) + ")";
    }

    /**
     * The destination of a link to the heading {@code anchor} of {@code chapter}, or to its start with an empty
     * anchor, written from {@code from}, the chapter the link is in; a link to a heading of its own chapter is the
     * anchor alone.
     */
    public static String chapterDestination(String from, String chapter, String anchor) {

        String place = anchor.isEmpty() ? "" : ANCHOR_MARK + anchor;
        if (chapter.equals(from) && !anchor.isEmpty())
            return place;

        return escape(NAME_SPECIAL, chapter, special -> "%%%02X".formatted((int) special.charAt(0)))
                + ChapterFolders.CHAPTER_EXTENSION + place;
    }

    /**
     * The web address the author typed as {@code typed}, with {@code https://} in front if it has no scheme, as
     * {@code www.example.com}, or {@code mailto:} if it is a mail address alone.
     */
    public static String webAddress(String typed) {

        String address = typed.strip();
        if (address.isEmpty() || ImageLines.isWebAddress(address))
            return address;

        return address.contains("@") && !address.contains("/") ? MAILTO + address : HTTPS + address;
    }

    // Reading links

    /**
     * Where a link found at {@code destination} leads, written in the chapter named {@code chapter}, which a link to
     * one of its own headings leads to.
     */
    public static LinkTarget target(String destination, String chapter) {

        if (ImageLines.isWebAddress(destination))
            return isWellFormed(destination) ? LinkTarget.WEB : LinkTarget.MALFORMED;

        int mark = destination.indexOf(ANCHOR_MARK);
        String path = mark < 0 ? destination : destination.substring(0, mark);
        Optional<String> anchor = mark < 0 ? Optional.of("") : decoded(destination.substring(mark + 1));
        if (anchor.isEmpty())
            return LinkTarget.MALFORMED;
        if (path.isEmpty())
            return mark < 0 ? LinkTarget.MALFORMED : LinkTarget.chapter(chapter, anchor.get());

        return decoded(path).flatMap(TextLinks::chapterNamed)
                .map(name -> LinkTarget.chapter(name, anchor.get()))
                .orElse(LinkTarget.MALFORMED);
    }

    /**
     * The {@code links} of {@code text}, the chapter named {@code chapter}, that lead nowhere: to a chapter or a
     * heading that is not there, or to a web address that is not well formed. A web address is never visited.
     *
     * @param links   the links of {@code text}, as {@link #of} finds them
     * @param anchors each chapter of the book by name, with the anchors of its headings; for {@code chapter} itself
     *                those of {@code text} are taken, as it is now
     */
    public static List<TextLink> broken(String text, List<TextLink> links, String chapter,
                                        Map<String, Set<String>> anchors) {

        if (links.isEmpty())
            return List.of();

        Set<String> own = HeadingAnchors.anchors(text);
        return links.stream().filter(link -> isBroken(link.destination(), chapter, own, anchors)).toList();
    }

    /**
     * True if a link found at {@code destination}, in the chapter named {@code chapter}, leads nowhere, as
     * {@link #broken} tells.
     *
     * @param own     the anchors of the chapter's own headings, as its text is now
     * @param anchors each chapter of the book by name, with the anchors of its headings
     */
    public static boolean isBroken(String destination, String chapter, Set<String> own,
                                   Map<String, Set<String>> anchors) {

        LinkTarget target = target(destination, chapter);
        return switch (target.kind()) {
            case WEB -> false;
            case MALFORMED -> true;
            case CHAPTER -> {
                Set<String> headings = target.chapter().equals(chapter) ? own : anchors.get(target.chapter());
                yield headings == null || !target.anchor().isEmpty() && !headings.contains(target.anchor());
            }
        };
    }

    // Changing links

    /** Removes {@code link} from {@code text}, keeping its text as written, which ends up selected. */
    public static TextEdit unlinked(String text, TextLink link) {

        String label = text.substring(link.label().start(), link.label().end());
        return TextEdit.selecting(link.range().start(), link.range().end(), label);
    }

    /**
     * {@code text}, a chapter's, with its links to the chapter {@code from}, just renamed, leading to {@code to}, its
     * new name; their text, heading and title are kept. Links to a heading of the chapter's own, written as the anchor
     * alone, stay as they are.
     */
    public static String withChapter(String text, String from, String to) {

        StringBuilder changed = new StringBuilder(text);
        for (TextLink link : of(text).reversed()) {
            if (link.destination().startsWith(ANCHOR_MARK))
                continue;

            LinkTarget target = target(link.destination(), "");
            if (target.chapter().equals(from))
                changed.replace(link.range().start(), link.range().end(),
                        relinked(text, link, chapterDestination("", to, target.anchor())));
        }

        return changed.toString();
    }

    /**
     * The Markdown of {@code link}, found in {@code text}, leading to {@code destination}: its text as written, with
     * its formatting, and its title are kept, as Modify link… keeps them when only the destination changes.
     */
    public static String relinked(String text, TextLink link, String destination) {

        String title = link.title().isEmpty() ? "" : " \"" + escape(TITLE_SPECIAL, link.title()) + "\"";
        return "[" + text.substring(link.label().start(), link.label().end()) + "](" + written(destination) + title
                + ")";
    }

    // Helpers

    /** {@code destination} as a link writes it, in angle brackets if it holds spaces or parentheses. */
    private static String written(String destination) {

        return DESTINATION_SPECIAL.matcher(destination).find() ? "<" + destination + ">" : destination;
    }

    private static String escape(Pattern special, String text) {

        return escape(special, text, match -> "\\" + match);
    }

    private static String escape(Pattern special, String text, Function<String, String> escaped) {

        return special.matcher(text).replaceAll(match -> Matcher.quoteReplacement(escaped.apply(match.group())));
    }

    /** True if {@code address} is a web address with a host, or a mail address with someone to write to. */
    private static boolean isWellFormed(String address) {

        try {
            URI uri = new URI(address);
            return switch (uri.getScheme().toLowerCase(Locale.ROOT)) {
                case "http", "https" -> uri.getHost() != null;
                case "mailto" -> !uri.getSchemeSpecificPart().isBlank();
                default -> false;
            };
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** The name of the chapter whose file {@code path} is, written from a chapter's; empty if it is no chapter's. */
    private static Optional<String> chapterNamed(String path) {

        try {
            Path file = CHAPTERS.resolve(path).normalize();
            String name = file.getFileName().toString();
            if (!CHAPTERS.equals(file.getParent()) || !name.endsWith(ChapterFolders.CHAPTER_EXTENSION)
                    || name.length() == ChapterFolders.CHAPTER_EXTENSION.length())
                return Optional.empty();

            return Optional.of(name.substring(0, name.length() - ChapterFolders.CHAPTER_EXTENSION.length()));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** {@code written} with its percent escapes undone, such as {@code %20} for a space; empty if one is broken. */
    private static Optional<String> decoded(String written) {

        try {
            // A plus is a plus in a link, not a space as in a form.
            return Optional.of(URLDecoder.decode(written.replace("+", "%2B"), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** True if the link at {@code range} is written inline, from its opening bracket to its closing parenthesis. */
    private static boolean isInline(String text, TextRange range) {

        return text.charAt(range.start()) == '[' && text.charAt(range.end() - 1) == ')';
    }

    /** Where the text of {@code link}, which spans {@code range}, is written, between its brackets. */
    private static TextRange label(Link link, TextRange range) {

        if (link.getFirstChild() == null)
            return new TextRange(range.start() + 1, range.start() + 1);

        return new TextRange(range(link.getFirstChild()).start(), range(link.getLastChild()).end());
    }

    /** Where {@code node}, parsed with its source spans, is written in the text. */
    static TextRange range(Node node) {

        List<SourceSpan> spans = node.getSourceSpans();
        SourceSpan last = spans.getLast();

        return new TextRange(spans.getFirst().getInputIndex(), last.getInputIndex() + last.getLength());
    }
}
