package it.infog.psiche.markdown.model;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The footnotes' marks in a chapter, each list in the order of the text.
 *
 * @param references each reference to a note, {@code [^moon]}
 * @param notes      each note's opening, {@code [^moon]:}
 */
public record FootnoteMarks(List<FootnoteMark> references,
                            List<FootnoteMark> notes) {

    /** Where every mark is, references and notes' openings together, in the order of the text. */
    public List<TextRange> ranges() {

        return Stream.concat(references.stream(), notes.stream())
                .map(FootnoteMark::range)
                .sorted(Comparator.comparingInt(TextRange::start))
                .toList();
    }
}
