package it.infog.psiche.markdown;

import it.infog.psiche.markdown.model.TextLink;
import it.infog.psiche.markdown.model.TextRange;

import java.util.List;
import java.util.Optional;

/**
 * The parts of a text the author changes as wholes, never from inside: in the editor's Default mode, its links, its
 * image lines and its footnotes' references and notes, which are changed from the menu. Given the blocks of a text, in
 * order and none overlapping, it tells where an edit by the author goes, where a caret that lands inside one goes, and
 * whether a selection can be formatted.
 */
public final class TextBlocks {

    private TextBlocks() {
    }

    /**
     * Where an edit by the author replacing the text from {@code start} to {@code end} goes: typing or pasting at a
     * caret inside a block is refused, and an edit over text that covers part of a block takes the whole block, as
     * Backspace right after one or Delete right before one does. Anything else goes as it is.
     *
     * @return the text the edit replaces, maybe wider; empty if it is refused
     */
    public static Optional<TextRange> edit(List<TextRange> blocks, int start, int end) {

        if (start == end)
            return inside(blocks, start).isPresent() ? Optional.empty() : Optional.of(new TextRange(start, end));

        int from = start;
        int to = end;
        for (TextRange block : blocks)
            if (block.start() < end && start < block.end()) {
                from = Math.min(from, block.start());
                to = Math.max(to, block.end());
            }

        return Optional.of(new TextRange(from, to));
    }

    /**
     * Where a caret at {@code position} goes: to the end of the block it lies inside if it moved {@code forward}, to
     * its start if not; a caret outside every block, or at an edge, stays.
     */
    public static int caret(List<TextRange> blocks, int position, boolean forward) {

        return inside(blocks, position).map(block -> forward ? block.end() : block.start()).orElse(position);
    }

    /** Where a caret put at {@code position}, as by a click, goes: to the nearer edge of the block it lies inside. */
    public static int nearestEdge(List<TextRange> blocks, int position) {

        return inside(blocks, position)
                .map(block -> position - block.start() <= block.end() - position ? block.start() : block.end())
                .orElse(position);
    }

    /**
     * True if formatting the selection from {@code start} to {@code end}, as bold or a heading, keeps every block
     * whole: it touches no closed block, as an image line, whose caption is changed only from its dialog, and each
     * link it reaches it either covers whole or lies within the link's text.
     *
     * @param closed the blocks of the text changed only from their dialogs, in order: its image lines, its footnotes'
     *               references and notes
     * @param links  the links of the text, in order
     */
    public static boolean formattable(List<TextRange> closed, List<TextLink> links, int start, int end) {

        TextRange selection = new TextRange(start, end);
        if (closed.stream().anyMatch(block -> block.overlaps(selection)))
            return false;

        return links.stream()
                .filter(link -> reaches(selection, link.range()))
                .allMatch(link -> selection.contains(link.range()) || link.label().contains(selection));
    }

    /** The block {@code position} lies strictly inside, past its start and before its end; if any. */
    public static Optional<TextRange> inside(List<TextRange> blocks, int position) {

        return blocks.stream().filter(block -> block.start() < position && position < block.end()).findFirst();
    }

    /** True if {@code selection} shares a character with {@code block}, or is a caret strictly inside it. */
    private static boolean reaches(TextRange selection, TextRange block) {

        return selection.start() < block.end() && block.start() < selection.end()
                || block.start() < selection.start() && selection.end() < block.end();
    }
}
