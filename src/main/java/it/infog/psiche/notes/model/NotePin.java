package it.infog.psiche.notes.model;

/**
 * Where a chapter note is pinned in its chapter's text: one paragraph, an editor line, which holds no other note's pin.
 * The paragraph's opening text finds it again when the text changed while the chapter was closed.
 *
 * @param paragraph the paragraph's number, counted from 0, as the editor counts its lines
 * @param text      the paragraph's first {@value #TEXT_LENGTH} characters, or all of them if it is shorter
 */
public record NotePin(int paragraph,
                      String text) {

    /** How much of the paragraph's opening text a pin keeps. */
    public static final int TEXT_LENGTH = 60;

    /** A pin to paragraph {@code paragraph}, whose text is {@code line}, of which it keeps the opening. */
    public static NotePin of(int paragraph, String line) {

        return new NotePin(paragraph, line.substring(0, Math.min(line.length(), TEXT_LENGTH)));
    }
}
