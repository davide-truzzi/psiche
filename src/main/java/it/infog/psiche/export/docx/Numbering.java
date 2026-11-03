package it.infog.psiche.export.docx;

import it.infog.psiche.export.document.model.enums.Style;

/**
 * The lists of a DOCX, for {@code numbering.xml}. That file defines the bullets and the numbers once, three levels
 * each, as the ODT's list styles do; every list of the text is an instance of one of them, so each numbered list starts
 * at its own number instead of going on from the one before.
 */
final class Numbering {

    // The ids of the bullets' and the numbers' definitions in numbering.xml.
    private static final int BULLETS = 0;
    private static final int NUMBERS = 1;
    /** The deepest level the definitions draw, from 0; lists nested further are drawn at it. */
    static final int DEEPEST_LEVEL = 2;
    // How far each level's text is indented, in twentieths of a point: 1.25cm, 2cm and 2.75cm, as in the ODT; the
    // same as numbering.xml's, which draws the bullets and numbers there.
    private static final int[] INDENTS = {709, 1134, 1559};

    private final StringBuilder instances = new StringBuilder();
    private int count;

    /**
     * A new list, numbered from {@code start} if it is a numbered one.
     *
     * @param level how deep the list is nested, from 0
     * @return its id, for the paragraphs of its items
     */
    int add(Style style, int level, int start) {

        count++;
        boolean numbered = style == Style.NUMBERING;
        instances.append("<w:num w:numId=\"%d\"><w:abstractNumId w:val=\"%d\"/>".formatted(count,
                numbered ? NUMBERS : BULLETS));
        if (numbered)
            instances.append("<w:lvlOverride w:ilvl=\"%d\"><w:startOverride w:val=\"%d\"/></w:lvlOverride>"
                    .formatted(level, start));
        instances.append("</w:num>\n");

        return count;
    }

    /** How far the text of a list's items at {@code level} is indented, for an item's later paragraphs. */
    static int indent(int level) {

        return INDENTS[Math.min(level, DEEPEST_LEVEL)];
    }

    /** The lists added so far, for {@code numbering.xml} after the definitions. */
    String xml() {

        return instances.toString();
    }
}
