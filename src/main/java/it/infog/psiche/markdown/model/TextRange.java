package it.infog.psiche.markdown.model;

/** The text from character {@code start} up to, but not including, character {@code end}. */
public record TextRange(int start,
                        int end) {

    /** True if this and {@code other} share a character, or {@code other}, empty, lies inside this. */
    public boolean overlaps(TextRange other) {

        return start < other.end && other.start < end || start <= other.start && other.end <= end;
    }
}
