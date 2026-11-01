package it.infog.psiche.notes.model;

import it.infog.psiche.notes.model.enums.NoteColor;

/**
 * Where a note sits on its chapter's board, of {@value #BOARD_WIDTH} × {@value #BOARD_HEIGHT} board pixels, and how it
 * looks there.
 *
 * @param x      the left edge, in board pixels
 * @param y      the top edge, in board pixels
 * @param width  the width, in board pixels
 * @param height the height, in board pixels
 * @param order  its place in the stack of notes: a note with a higher order lies over one with a lower
 * @param color  its colour
 */
public record NoteLayout(double x,
                         double y,
                         double width,
                         double height,
                         int order,
                         NoteColor color) {

    public static final double BOARD_WIDTH = 1600;
    public static final double BOARD_HEIGHT = 2500;
}
