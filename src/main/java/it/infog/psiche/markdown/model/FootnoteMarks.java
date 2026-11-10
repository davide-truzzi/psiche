package it.infog.psiche.markdown.model;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The footnotes' marks in a chapter, each list in the order of the text.
 *
 * @param references each reference to a note, {@code [^moon]}
 * @param notes      each note's opening, {@code [^moon]:}
 * @param missing    each reference to no note, which stays as it is written
 */
public record FootnoteMarks(List<FootnoteMark> references,
                            List<FootnoteMark> notes,
                            List<FootnoteMark> missing) {

    /** Where every mark is, references, notes' openings and references to no note, in the order of the text. */
    public List<TextRange> ranges() {

        return Stream.of(references, notes, missing).flatMap(List::stream)
                .map(FootnoteMark::range)
                .sorted(Comparator.comparingInt(TextRange::start))
                .toList();
    }
}
