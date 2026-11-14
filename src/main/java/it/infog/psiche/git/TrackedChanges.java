package it.infog.psiche.git;

import it.infog.psiche.git.model.Comparison;
import it.infog.psiche.git.model.Paragraph;
import it.infog.psiche.git.model.Segment;
import it.infog.psiche.git.model.Segment.Change;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.diff.SequenceComparator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares a past version of a chapter with the chapter now, the way a word processor tracks changes. The lines that
 * differ are found as Git finds them; each changed line is then paired with the most similar line of the other text
 * and the two are compared word by word, so only the words that changed are marked. A line with no similar partner was
 * written or removed as a whole.
 */
public final class TrackedChanges {

    // A change adds or removes whole words, runs of spaces or single punctuation marks.
    private static final Pattern TOKEN = Pattern.compile("\\s+|[\\p{L}\\p{N}\\p{M}'’]+|.");
    // A word as a writer counts it; punctuation does not count.
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}\\p{M}'’]+");
    // Below this share of words in common, two lines read better as one removed and another written than as an edit.
    private static final double SIMILAR = 0.5;

    private TrackedChanges() {
    }

    /**
     * @param then the chapter as the version recorded it
     * @param now  the chapter as it is now
     */
    public static Comparison compare(String then, String now) {

        Tokens thenLines = new Tokens(lines(then));
        Tokens nowLines = new Tokens(lines(now));
        List<Paragraph> paragraphs = new ArrayList<>();
        int next = 0;

        for (Edit edit : new HistogramDiff().diff(Tokens.EQUALITY, thenLines, nowLines)) {
            thenLines.items.subList(next, edit.getBeginA()).forEach(line -> paragraphs.add(whole(line, Change.UNCHANGED)));
            paragraphs.addAll(changedPassage(thenLines.items.subList(edit.getBeginA(), edit.getEndA()),
                    nowLines.items.subList(edit.getBeginB(), edit.getEndB())));
            next = edit.getEndA();
        }

        thenLines.items.subList(next, thenLines.size()).forEach(line -> paragraphs.add(whole(line, Change.UNCHANGED)));
        return new Comparison(paragraphs, count(paragraphs, null), count(paragraphs, Change.ADDED),
                count(paragraphs, Change.REMOVED), words(paragraphs, Change.ADDED), words(paragraphs, Change.REMOVED));
    }

    /** The words in {@code text}, as a writer counts them: runs of letters and digits, apostrophes included. */
    public static int countWords(String text) {

        return (int) WORD.matcher(text).results().count();
    }

    /** Lines without their line breaks; a final line break does not start another, empty line. */
    private static List<String> lines(String text) {

        String normalized = text.replace("\r\n", "\n");
        if (normalized.isEmpty())
            return List.of();

        List<String> lines = new ArrayList<>(List.of(normalized.split("\n", -1)));
        if (normalized.endsWith("\n"))
            lines.removeLast();
        return lines;
    }

    private static Paragraph whole(String line, Change change) {
        return new Paragraph(List.of(new Segment(line, change)));
    }

    /**
     * The lines of one changed passage in reading order: each pair of similar lines compared word by word, and before
     * it the lines of either text that have no partner, the removed ones first.
     */
    private static List<Paragraph> changedPassage(List<String> oldLines, List<String> newLines) {

        List<Paragraph> paragraphs = new ArrayList<>();
        int oldNext = 0;
        int newNext = 0;

        for (int[] pair : similarPairs(oldLines, newLines)) {
            oldLines.subList(oldNext, pair[0]).forEach(line -> paragraphs.add(whole(line, Change.REMOVED)));
            newLines.subList(newNext, pair[1]).forEach(line -> paragraphs.add(whole(line, Change.ADDED)));
            paragraphs.add(wordByWord(oldLines.get(pair[0]), newLines.get(pair[1])));
            oldNext = pair[0] + 1;
            newNext = pair[1] + 1;
        }

        oldLines.subList(oldNext, oldLines.size()).forEach(line -> paragraphs.add(whole(line, Change.REMOVED)));
        newLines.subList(newNext, newLines.size()).forEach(line -> paragraphs.add(whole(line, Change.ADDED)));
        return paragraphs;
    }

    /**
     * Pairs lines of the two texts, keeping their order, so that the pairs are as similar as possible in total; lines
     * less than {@link #SIMILAR} alike are never paired.
     *
     * @return the pairs as {old line index, new line index}, in reading order
     */
    private static List<int[]> similarPairs(List<String> oldLines, List<String> newLines) {

        //best[i][j]: the best total similarity of pairs among the first i old lines and the first j new lines
        int rows = oldLines.size();
        int columns = newLines.size();
        double[][] similarity = new double[rows][columns];
        double[][] best = new double[rows + 1][columns + 1];
        for (int i = 1; i <= rows; i++) {
            for (int j = 1; j <= columns; j++) {
                similarity[i - 1][j - 1] = similarity(oldLines.get(i - 1), newLines.get(j - 1));
                best[i][j] = Math.max(best[i - 1][j], best[i][j - 1]);
                if (similarity[i - 1][j - 1] >= SIMILAR)
                    best[i][j] = Math.max(best[i][j], best[i - 1][j - 1] + similarity[i - 1][j - 1]);
            }
        }

        //walk back from the end to read the pairs that make up the best total
        List<int[]> pairs = new ArrayList<>();
        int i = rows;
        int j = columns;
        while (i > 0 && j > 0) {
            if (best[i][j] == best[i - 1][j])
                i--;
            else if (best[i][j] == best[i][j - 1])
                j--;
            else {
                pairs.addFirst(new int[] {i - 1, j - 1});
                i--;
                j--;
            }
        }
        return pairs;
    }

    /** The share of words two lines have in common, from 0 to 1; spaces do not count. */
    private static double similarity(String a, String b) {

        List<String> aWords = words(a);
        List<String> bWords = words(b);
        if (aWords.isEmpty() || bWords.isEmpty())
            return 0;

        Map<String, Integer> remaining = new HashMap<>();
        aWords.forEach(word -> remaining.merge(word, 1, Integer::sum));
        int common = 0;
        for (String word : bWords) {
            if (remaining.getOrDefault(word, 0) > 0) {
                remaining.merge(word, -1, Integer::sum);
                common++;
            }
        }
        return 2.0 * common / (aWords.size() + bWords.size());
    }

    private static List<String> words(String line) {
        return tokens(line).stream().filter(token -> !token.isBlank()).toList();
    }

    private static List<String> tokens(String text) {

        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find())
            tokens.add(matcher.group());
        return tokens;
    }

    /** One line compared with its partner: the new line's text, with the old words it replaced kept in place. */
    private static Paragraph wordByWord(String oldLine, String newLine) {

        Tokens oldWords = new Tokens(tokens(oldLine));
        Tokens newWords = new Tokens(tokens(newLine));
        List<Segment> segments = new ArrayList<>();
        int next = 0;

        for (Edit edit : joinAcrossSpaces(new HistogramDiff().diff(Tokens.EQUALITY, oldWords, newWords), oldWords)) {
            append(segments, oldWords.items.subList(next, edit.getBeginA()), Change.UNCHANGED);
            append(segments, oldWords.items.subList(edit.getBeginA(), edit.getEndA()), Change.REMOVED);
            append(segments, newWords.items.subList(edit.getBeginB(), edit.getEndB()), Change.ADDED);
            next = edit.getEndA();
        }

        append(segments, oldWords.items.subList(next, oldWords.size()), Change.UNCHANGED);
        return new Paragraph(segments);
    }

    /**
     * Merges changes separated only by a space, so "the old grey cat" to "a new black cat" reads as one phrase
     * replaced rather than three words struck and three written, alternating.
     */
    private static List<Edit> joinAcrossSpaces(List<Edit> edits, Tokens oldWords) {

        List<Edit> joined = new ArrayList<>();
        for (Edit edit : edits) {
            Edit last = joined.isEmpty() ? null : joined.getLast();
            if (last != null && oldWords.items.subList(last.getEndA(), edit.getBeginA()).stream().allMatch(String::isBlank))
                joined.set(joined.size() - 1,
                        new Edit(last.getBeginA(), edit.getEndA(), last.getBeginB(), edit.getEndB()));
            else
                joined.add(edit);
        }
        return joined;
    }

    /** Adds the words as one run, or to the last run if it has the same change. */
    private static void append(List<Segment> segments, List<String> words, Change change) {

        if (words.isEmpty())
            return;

        String text = String.join("", words);
        if (!segments.isEmpty() && segments.getLast().change() == change)
            segments.set(segments.size() - 1, new Segment(segments.getLast().text() + text, change));
        else
            segments.add(new Segment(text, change));
    }

    /**
     * Paragraphs the way a writer counts them, so blank lines do not count.
     *
     * @param change {@link Change#ADDED} or {@link Change#REMOVED} for whole paragraphs written or removed, or
     *               {@code null} for paragraphs that were edited
     */
    private static int count(List<Paragraph> paragraphs, Change change) {
        return (int) paragraphs.stream()
                .filter(paragraph -> paragraph.segments().stream().anyMatch(segment -> !segment.text().isBlank()))
                .filter(paragraph -> change == null
                        ? paragraph.segments().size() > 1 && paragraph.changed()
                        : paragraph.segments().stream().allMatch(segment -> segment.change() == change))
                .count();
    }

    /** The words in the runs of text with {@code change}, as {@link #countWords} counts them. */
    private static int words(List<Paragraph> paragraphs, Change change) {

        return paragraphs.stream()
                .flatMap(paragraph -> paragraph.segments().stream())
                .filter(segment -> segment.change() == change)
                .mapToInt(segment -> countWords(segment.text()))
                .sum();
    }

    /** A list of lines or words that JGit's diff algorithms can compare. */
    private static final class Tokens extends Sequence {

        static final SequenceComparator<Tokens> EQUALITY = new SequenceComparator<>() {
            @Override
            public boolean equals(Tokens a, int ai, Tokens b, int bi) {
                return a.items.get(ai).equals(b.items.get(bi));
            }

            @Override
            public int hash(Tokens sequence, int index) {
                return sequence.items.get(index).hashCode();
            }
        };

        final List<String> items;

        Tokens(List<String> items) {
            this.items = items;
        }

        @Override
        public int size() {
            return items.size();
        }
    }
}
