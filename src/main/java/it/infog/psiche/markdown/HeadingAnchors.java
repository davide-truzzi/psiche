package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.HeadingAnchor;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Heading;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The anchors of a chapter's headings, which links write after {@code #} to lead to them. They follow GitHub's rule,
 * so links work there too: the heading's text in lower case, its spaces turned into hyphens, everything but letters,
 * digits, hyphens and underscores left out; a heading whose anchor an earlier one took gets {@code -1}, {@code -2}.
 * Every part of Psiche that names a heading, the editor, the preview and the exports, takes its anchor from here.
 */
public final class HeadingAnchors {

    // What an anchor leaves out of a heading: anything but letters with their accents, digits, spaces, hyphens and
    // underscores.
    private static final Pattern LEFT_OUT = Pattern.compile("[^\\p{L}\\p{M}\\p{N}\\p{Pc} -]");

    private static final Parser PARSER = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build();

    private HeadingAnchors() {
    }

    /** The headings of {@code text}, in order, each with its anchor. */
    public static List<HeadingAnchor> of(String text) {

        List<HeadingAnchor> headings = new ArrayList<>();
        Function<Heading, String> namer = namer();
        PARSER.parse(text).accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {

                headings.add(new HeadingAnchor(heading.getLevel(), PlainText.of(heading), namer.apply(heading)));
            }
        });

        return headings;
    }

    /** Where the heading of {@code text} named {@code anchor} starts; empty if it has none of that name. */
    public static OptionalInt start(String text, String anchor) {

        List<Integer> starts = new ArrayList<>();
        Function<Heading, String> namer = namer();
        PARSER.parse(text).accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {

                List<SourceSpan> spans = heading.getSourceSpans();
                if (namer.apply(heading).equals(anchor) && !spans.isEmpty())
                    starts.add(spans.getFirst().getInputIndex());
            }
        });

        return starts.isEmpty() ? OptionalInt.empty() : OptionalInt.of(starts.getFirst());
    }

    /**
     * Names the headings of one parsed text, given in the order they come, as {@link #of} names them: for a renderer
     * that walks a parsed chapter and marks each heading with its anchor. A new one is needed for each text.
     */
    public static Function<Heading, String> namer() {

        Set<String> taken = new HashSet<>();
        return heading -> unique(anchor(PlainText.of(heading)), taken);
    }

    /** The anchors of the headings of {@code text}, in order. */
    public static Set<String> anchors(String text) {

        Set<String> anchors = new LinkedHashSet<>();
        for (HeadingAnchor heading : of(text))
            anchors.add(heading.anchor());

        return anchors;
    }

    /** The anchor of a heading reading {@code text}, before it is made unique in its chapter. */
    public static String anchor(String text) {

        return LEFT_OUT.matcher(text.strip().toLowerCase(Locale.ROOT)).replaceAll("").replace(' ', '-');
    }

    /** {@code anchor}, or with the first number that makes it one {@code taken} does not hold, which it adds to. */
    private static String unique(String anchor, Set<String> taken) {

        String unique = anchor;
        for (int repeat = 1; !taken.add(unique); repeat++)
            unique = anchor + "-" + repeat;

        return unique;
    }
}
