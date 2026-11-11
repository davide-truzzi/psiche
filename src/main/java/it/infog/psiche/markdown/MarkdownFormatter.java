package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.Script;
import it.infog.psiche.markdown.model.Subscript;
import it.infog.psiche.markdown.model.Superscript;
import it.infog.psiche.markdown.model.TextEdit;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Delimited;
import org.commonmark.node.Emphasis;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Markdown formatting of the selected text in a chapter. Each method takes the whole text and the selection, and
 * returns the edit that formats it, selecting the formatted text. Bold, italic, strikethrough, subscript and
 * superscript wrap the selection, joining the same formatting it overlaps instead of doubling it; headings, lists and
 * quotes apply to every line the selection touches, or the caret's line when nothing is selected. A comment hides text
 * from the preview and exports.
 */
public final class MarkdownFormatter {

    /*
     * The start of a line: up to three spaces and any quote markers, then a heading, bullet or number marker. Group 1
     * is the quote part, group 2 the marker, group 3 the rest of the line.
     */
    private static final Pattern LINE = Pattern.compile("^( {0,3}(?:>[ \\t]?)*)(#{1,6}(?:[ \\t]+|$)|[-*+][ \\t]+|\\d{1,9}[.)][ \\t]+)?(.*)$");
    // A scene break such as --- or * * *, which a bullet pattern would take for a list item.
    private static final Pattern SCENE_BREAK = Pattern.compile("^ {0,3}([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$");

    private static final String STRIKETHROUGH = "~~";
    private static final String COMMENT_OPEN = "<!--";
    private static final String COMMENT_CLOSE = "-->";
    // The markers of comments inside a selection being commented, with the space Comment puts inside them.
    private static final Pattern COMMENT_MARKER = Pattern.compile("<!-- ?| ?-->");

    private static final Parser PARSER = MarkdownSyntax.parser(IncludeSourceSpans.BLOCKS_AND_INLINES);

    private MarkdownFormatter() {
    }

    public static TextEdit bold(String text, int start, int end) {
        return wrap(text, start, end, "**");
    }

    public static TextEdit italic(String text, int start, int end) {
        return wrap(text, start, end, "*");
    }

    public static TextEdit strikethrough(String text, int start, int end) {
        return wrap(text, start, end, STRIKETHROUGH);
    }

    /** Wraps the selection in tildes; see {@link #fitsScript}. */
    public static TextEdit subscript(String text, int start, int end) {
        return wrap(text, start, end, String.valueOf(Subscript.SIGN));
    }

    /** Wraps the selection in carets; see {@link #fitsScript}. */
    public static TextEdit superscript(String text, int start, int end) {
        return wrap(text, start, end, String.valueOf(Superscript.SIGN));
    }

    /**
     * True if the selection can become a subscript or a superscript: some text, with no space or line break in it,
     * which would leave the signs as they are written.
     */
    public static boolean fitsScript(String text, int start, int end) {
        return start < end && text.substring(start, end).chars().noneMatch(Character::isWhitespace);
    }

    /** Makes each line a heading of {@code level}, replacing any heading or list marker it has. */
    public static TextEdit heading(String text, int start, int end, int level) {
        return formatLines(text, start, end, (line, number) -> line.group(1) + "#".repeat(level) + " " + line.group(3));
    }

    /** Puts a bullet before each line, replacing any heading or list marker it has. */
    public static TextEdit bulletedList(String text, int start, int end) {
        return formatLines(text, start, end, (line, number) -> line.group(1) + "- " + line.group(3));
    }

    /** Numbers the lines from 1, replacing any heading or list marker they have. */
    public static TextEdit numberedList(String text, int start, int end) {
        return formatLines(text, start, end, (line, number) -> line.group(1) + number + ". " + line.group(3));
    }

    /** Quotes every line, empty ones too so the paragraphs stay in one quote; a quote inside it nests. */
    public static TextEdit quote(String text, int start, int end) {

        int from = lineStart(text, start);
        int to = lineEnd(text, start, end);

        String quoted = Arrays.stream(text.substring(from, to).split("\n", -1))
                .map(line -> line.isBlank() ? ">" : "> " + line)
                .collect(Collectors.joining("\n"));

        return TextEdit.selecting(from, to, quoted);
    }

    /**
     * Removes heading, list and quote markers from the lines the selection touches, and the marks of any bold, italic,
     * strikethrough, subscript or superscript that overlaps the selection, as a whole. Scene breaks, and asterisks,
     * tildes and carets that format nothing, stay.
     */
    public static TextEdit clearFormatting(String text, int start, int end) {

        BitSet removed = new BitSet(text.length());
        int from = lineStart(text, start);
        int to = lineEnd(text, start, end);

        //line markers
        for (int lineStart = from; lineStart <= to; ) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0 || lineEnd > to)
                lineEnd = to;
            String line = text.substring(lineStart, lineEnd);
            if (!SCENE_BREAK.matcher(line).matches()) {
                Matcher matcher = LINE.matcher(line);
                if (matcher.matches())
                    removed.set(lineStart, lineStart + matcher.start(3));
            }
            lineStart = lineEnd + 1;
        }

        //bold, italic, strikethrough, subscript, superscript
        for (Span span : formatted(text)) {
            if (start < span.end() && end > span.start()) {
                removed.set(span.start(), span.start() + span.delimiter().length());
                removed.set(span.end() - span.delimiter().length(), span.end());
                from = Math.min(from, span.start());
                to = Math.max(to, span.end());
            }
        }

        StringBuilder cleared = new StringBuilder();
        for (int i = from; i < to; i++)
            if (!removed.get(i))
                cleared.append(text.charAt(i));

        return TextEdit.selecting(from, to, cleared.toString());
    }

    /**
     * Makes the selection an HTML comment, which the preview and exports leave out, or text again if it is in one: the
     * comment around the selection, or the caret, loses its markers. With nothing selected, the caret's line is
     * commented, or on an empty line an empty comment is put at the caret. A selection over several lines takes them
     * whole, so the comment starts a line and may run over blank ones, which one starting inside a line cannot;
     * comments inside the selection join it, as comments cannot nest. What was commented, or uncommented, stays
     * selected, so the same command undoes it.
     */
    public static TextEdit comment(String text, int start, int end) {

        //in a comment: uncomment it
        Optional<Range> around = commentAround(text, start, end);
        if (around.isPresent())
            return TextEdit.selecting(around.get().start(), around.get().end(),
                    uncommented(text.substring(around.get().start(), around.get().end())));

        //on an empty line: an empty comment, the caret inside
        int from = lineStart(text, start);
        int to = lineEnd(text, start, end);
        if (start == end && text.substring(from, to).isBlank()) {
            int inside = start + COMMENT_OPEN.length() + 1;
            return new TextEdit(start, end, COMMENT_OPEN + "  " + COMMENT_CLOSE, inside, inside);
        }

        //the selection inside a line, or whole lines
        if (start != end && !text.substring(start, end).contains("\n")) {
            from = start;
            to = end;
        }
        String inner = COMMENT_MARKER.matcher(text.substring(from, to)).replaceAll("");
        int innerStart = from + COMMENT_OPEN.length() + 1;

        return new TextEdit(from, to, COMMENT_OPEN + " " + inner + " " + COMMENT_CLOSE, innerStart,
                innerStart + inner.length());
    }

    /** True if the selection touches at most one line that is not empty, so it can become a heading. */
    public static boolean spansOneLine(String text, int start, int end) {
        return text.substring(lineStart(text, start), lineEnd(text, start, end)).lines()
                .filter(line -> !line.isBlank())
                .count() <= 1;
    }

    /**
     * Wraps the words of each selected line in {@code marker}, as formatting cannot cross a paragraph and would break
     * a list item or heading it spanned. Spaces at either end, and the line's own markers, stay outside. Text that
     * already has this formatting is not wrapped again: words inside it stay as they are, and words overlapping or
     * touching it join it, so bolding "rem ip" in {@code **lorem** ipsum} gives {@code **lorem ip**sum}.
     */
    private static TextEdit wrap(String text, int start, int end, String marker) {

        List<Span> existing = formatted(text).stream()
                .filter(span -> span.delimiter().replace('_', '*').equals(marker))
                .toList();
        BitSet removed = new BitSet(text.length());
        List<Range> wrapped = new ArrayList<>();

        //join each line's words with the emphasis they overlap or touch
        for (Range words : selectedWords(text, start, end)) {
            if (existing.stream().anyMatch(span -> span.start() <= words.start() && span.end() >= words.end()))
                continue;

            int from = words.start();
            int to = words.end();
            for (Span span : existing)
                if (span.start() <= words.end() && span.end() >= words.start()) {
                    removed.set(span.start(), span.start() + span.delimiter().length());
                    removed.set(span.end() - span.delimiter().length(), span.end());
                    from = Math.min(from, span.start());
                    to = Math.max(to, span.end());
                }

            // Lines that share an emphasis across a line break become one range.
            if (!wrapped.isEmpty() && from <= wrapped.getLast().end())
                wrapped.add(new Range(Math.min(wrapped.removeLast().start(), from), to));
            else
                wrapped.add(new Range(from, to));
        }

        if (wrapped.isEmpty())
            return TextEdit.selecting(start, end, text.substring(start, end));

        //rewrite, with the joined emphasis' own markers left out
        int from = Math.min(start, wrapped.getFirst().start());
        int to = Math.max(end, wrapped.getLast().end());
        StringBuilder result = new StringBuilder();
        for (int i = from; i <= to; i++) {
            for (Range range : wrapped)
                if (range.end() == i || range.start() == i)
                    result.append(marker);
            if (i < to && !removed.get(i))
                result.append(text.charAt(i));
        }

        return TextEdit.selecting(from, to, result.toString());
    }

    /** The part of each line from {@code start} to {@code end} without spaces at either end or the line's markers. */
    private static List<Range> selectedWords(String text, int start, int end) {

        List<Range> words = new ArrayList<>();

        for (int lineStart = start; lineStart <= end; ) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0 || lineEnd > end)
                lineEnd = end;

            // A selection that starts mid-line has no markers in it to skip.
            int from = lineStart == lineStart(text, lineStart)
                    ? lineStart + markerLength(text.substring(lineStart, lineEnd))
                    : lineStart;
            int to = lineEnd;
            while (from < to && Character.isWhitespace(text.charAt(from)))
                from++;
            while (to > from && Character.isWhitespace(text.charAt(to - 1)))
                to--;
            if (from < to)
                words.add(new Range(from, to));

            lineStart = lineEnd + 1;
        }

        return words;
    }

    private static int markerLength(String line) {

        if (SCENE_BREAK.matcher(line).matches())
            return 0;

        Matcher matcher = LINE.matcher(line);
        return matcher.matches() ? matcher.start(3) : 0;
    }

    /**
     * Rewrites each non-empty line the selection touches with {@code format}, which is given the line split by
     * {@link #LINE} and its number among those lines, from 1. Empty lines stay as they are.
     */
    private static TextEdit formatLines(String text, int start, int end, LineFormat format) {

        int from = lineStart(text, start);
        int to = lineEnd(text, start, end);

        List<String> lines = new ArrayList<>();
        int number = 0;
        for (String line : text.substring(from, to).split("\n", -1)) {
            Matcher matcher = LINE.matcher(line);
            if (line.isBlank() || SCENE_BREAK.matcher(line).matches() || !matcher.matches())
                lines.add(line);
            else
                lines.add(format.apply(matcher, ++number));
        }

        return TextEdit.selecting(from, to, String.join("\n", lines));
    }

    /** Every bold, italic, strikethrough, subscript or superscript span in {@code text}. */
    private static List<Span> formatted(String text) {

        List<Span> spans = new ArrayList<>();
        PARSER.parse(text).accept(new AbstractVisitor() {
            @Override
            public void visit(Emphasis emphasis) {
                add(emphasis);
                visitChildren(emphasis);
            }

            @Override
            public void visit(StrongEmphasis strong) {
                add(strong);
                visitChildren(strong);
            }

            @Override
            public void visit(CustomNode node) {
                switch (node) {
                    case Strikethrough strikethrough -> add(strikethrough);
                    case Script script -> add(script);
                    default -> {}
                }
                visitChildren(node);
            }

            private <T extends Node & Delimited> void add(T node) {
                List<SourceSpan> sourceSpans = node.getSourceSpans();
                if (sourceSpans.isEmpty())
                    return;
                SourceSpan first = sourceSpans.getFirst();
                SourceSpan last = sourceSpans.getLast();
                spans.add(new Span(first.getInputIndex(),
                                   last.getInputIndex() + last.getLength(),
                                   node.getOpeningDelimiter()));
            }
        });

        return spans;
    }

    /**
     * The comment the selection lies in, markers included, if it lies in one; a comment left open runs to the end of
     * the text, as it does in a browser.
     */
    private static Optional<Range> commentAround(String text, int start, int end) {

        int open = text.lastIndexOf(COMMENT_OPEN, start);
        if (open < 0)
            return Optional.empty();

        int close = text.indexOf(COMMENT_CLOSE, open + COMMENT_OPEN.length());
        int closeEnd = close < 0 ? text.length() : close + COMMENT_CLOSE.length();

        return end <= closeEnd ? Optional.of(new Range(open, closeEnd)) : Optional.empty();
    }

    /** {@code comment} without its markers, nor the space Comment puts inside each. */
    private static String uncommented(String comment) {

        String inner = comment.substring(COMMENT_OPEN.length());
        if (inner.startsWith(" "))
            inner = inner.substring(1);
        if (inner.endsWith(COMMENT_CLOSE))
            inner = inner.substring(0, inner.length() - COMMENT_CLOSE.length());
        if (inner.endsWith(" "))
            inner = inner.substring(0, inner.length() - 1);

        return inner;
    }

    private static int lineStart(String text, int index) {
        return text.lastIndexOf('\n', index - 1) + 1;
    }

    /**
     * The end of the selection's last line. A selection that ends at the very start of a line, as one made by dragging
     * over whole lines does, stops at the line before.
     */
    private static int lineEnd(String text, int start, int end) {

        int last = end > start && lineStart(text, end) == end ? end - 1 : end;
        int lineEnd = text.indexOf('\n', last);

        return lineEnd < 0 ? text.length() : lineEnd;
    }

    /** Text from {@code start} up to {@code end}. */
    private record Range(int start,
                         int end) {
    }

    /** Formatted text from {@code start} to {@code end}, including the {@code delimiter} on each side. */
    private record Span(int start,
                        int end,
                        String delimiter) {
    }

    @FunctionalInterface
    private interface LineFormat {
        String apply(Matcher line, int number);
    }
}
