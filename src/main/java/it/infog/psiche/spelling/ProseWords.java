package it.infog.psiche.spelling;

import it.infog.psiche.markdown.MarkdownSyntax;
import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.spelling.model.Word;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words of a chapter's prose, where its spelling is checked: in its paragraphs, headings, lists, quotes and the
 * text of its links, but not in its code, its HTML and comments, the targets of its links and images, web and mail
 * addresses, HTML entities, or words with digits in them. A word keeps the apostrophes and hyphens inside it, as in
 * "dell'anima" and "peut-être", and a final apostrophe, as in "po'".
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
        PARSER.parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                text.getSourceSpans().forEach(span -> addWords(markdown, span, words));
            }
        });

        return words;
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
            if (DIGIT.matcher(word.group()).find() || skipped.stream().anyMatch(each -> overlap(each, range)))
                continue;
            words.add(new Word(word.group(), range));
        }
    }

    private static boolean overlap(TextRange a, TextRange b) {

        return a.start() < b.end() && b.start() < a.end();
    }
}
