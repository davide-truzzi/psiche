package it.infog.psiche.markdown.model;

import it.infog.psiche.markdown.model.enums.FootnotePart;

/**
 * A part of a footnote, where something in the text lies, as the right-click menu.
 *
 * @param mark  the reference or note's opening, with its block
 * @param part  what part of a footnote it is
 * @param wrong true if it is a reference to no note, a note nothing refers to or a second note of a label
 */
public record FootnotePlace(FootnoteMark mark,
                            FootnotePart part,
                            boolean wrong) {
}
