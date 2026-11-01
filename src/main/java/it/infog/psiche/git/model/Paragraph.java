package it.infog.psiche.git.model;

import java.util.List;

/** One line of Markdown, which is a paragraph, a heading or a blank line, as a sequence of tracked runs of text. */
public record Paragraph(List<Segment> segments) {

    public Paragraph {
        segments = List.copyOf(segments);
    }

    /** True if any of the paragraph was written or removed since the version. */
    public boolean changed() {
        return segments.stream().anyMatch(segment -> segment.change() != Segment.Change.UNCHANGED);
    }
}
