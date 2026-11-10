package it.infog.psiche.spelling;

import it.infog.psiche.markdown.MarkdownSyntax;
import it.infog.psiche.markdown.model.Script;
import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.spelling.model.Word;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomNode;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words of a chapter's prose, where its spelling is checked: in its paragraphs, headings, lists, quotes, the
 * text of its links and its footnotes, but not in its code, its HTML and comments, the targets of its links and
 * images, its footnotes' labels, web and mail addresses, HTML entities, or words with digits in them. A word keeps the
 * apostrophes and hyphens inside it, as in "dell'anima" and "peut-être", and a final apostrophe, as in "po'"; one
 * split by a subscript's or a superscript's signs is one word without them, as M^lle^ is Mlle.
 */
public final class ProseWords {

    // Inside a word, between its letters: apostrophes, straight or curly, and hyphens.
    private static final String JOINERS = "'’-";
    // Letters, with joiners between them, and maybe a final apostrophe.
    private static final Pattern WORD =
            Pattern.compile("[\\p{L}\\p{M}\\p{N}]+(?:[" + JOINERS + "][\\p{L}\\p{M}\\p{N}]+)*['’]?");
    private static final Pattern DIGIT = Pattern.compile("\\p{N}");
    // Written in the prose but not words: web and mail addresses, and HTML entities such as &amp;.
    private static final Pattern NOT_WORDS = Pattern.compile("(?:https?://|www\\.)\\S+|\\S+@\\S+\\.\\S+|&#?\\w+;");
    private static final Parser PARSER = MarkdownSyntax.parser(IncludeSourceSpans.BLOCKS_AND_INLINES);

    private ProseWords() {
    }

    /** The words of {@code markdown}'s prose, in order. */
    public static List<Word> of(String markdown) {

        List<Word> words = new ArrayList<>();
        // The signs of the subscripts and superscripts, which a word goes on across.
        BitSet signs = new BitSet(markdown.length());
        PARSER.parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                text.getSourceSpans().forEach(span -> addWords(markdown, span, words));
            }

            @Override
            public void visit(CustomNode node) {
                List<SourceSpan> spans = node.getSourceSpans();
                if (node instanceof Script && !spans.isEmpty()) {
                    signs.set(spans.getFirst().getInputIndex());
                    signs.set(spans.getLast().getInputIndex() + spans.getLast().getLength() - 1);
                }
                visitChildren(node);
            }
        });

        return joined(words, signs).stream().filter(word -> !DIGIT.matcher(word.text()).find()).toList();
    }

    /** True if {@code character} can be part of a word: a letter, a digit, an apostrophe or a hyphen. */
    public static boolean isWordCharacter(char character) {

        return Character.isLetterOrDigit(character) || JOINERS.indexOf(character) >= 0;
    }

    /** Adds the words of {@code markdown} inside {@code span}, a run of prose, to {@code words}. */
    private static void addWords(String markdown, SourceSpan span, List<Word> words) {

        int start = span.getInputIndex();
        int end = start + span.getLength();

        //what looks like words but is not
        List<TextRange> skipped = new ArrayList<>();
        Matcher notWords = NOT_WORDS.matcher(markdown).region(start, end);
        while (notWords.find())
            skipped.add(new TextRange(notWords.start(), notWords.end()));

        //the words
        Matcher word = WORD.matcher(markdown).region(start, end);
        while (word.find()) {
            TextRange range = new TextRange(word.start(), word.end());
            if (skipped.stream().anyMatch(each -> overlap(each, range)))
                continue;
            words.add(new Word(word.group(), range));
        }
    }

    /** {@code words}, with those that only {@code signs} part joined into one. */
    private static List<Word> joined(List<Word> words, BitSet signs) {

        List<Word> joined = new ArrayList<>();
        for (Word word : words)
            if (!joined.isEmpty() && onlySigns(signs, joined.getLast().range().end(), word.range().start()))
                joined.add(join(joined.removeLast(), word, signs));
            else
                joined.add(word);

        return joined;
    }

    /**
     * {@code before} and {@code after} as one word, over the signs between them and the one closing it, if any, so a
     * suggestion put in its place leaves no sign behind.
     */
    private static Word join(Word before, Word after, BitSet signs) {

        int end = after.range().end() + (signs.get(after.range().end()) ? 1 : 0);
        return new Word(before.text() + after.text(), new TextRange(before.range().start(), end));
    }

    /** True if every character from {@code from} up to {@code to} is a sign. */
    private static boolean onlySigns(BitSet signs, int from, int to) {

        return from <= to && signs.nextClearBit(from) >= to;
    }

    private static boolean overlap(TextRange a, TextRange b) {

        return a.start() < b.end() && b.start() < a.end();
    }
}
