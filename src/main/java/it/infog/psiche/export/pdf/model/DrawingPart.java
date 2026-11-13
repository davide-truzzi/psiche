package it.infog.psiche.export.pdf.model;

import java.awt.Shape;

/**
 * One shape of an SVG drawing, as Java 2D draws it.
 *
 * @param filled      true if its inside is painted
 * @param stroked     true if its outline is drawn, {@code strokeWidth} wide, with round ends
 * @param strokeWidth the outline's width, in the drawing's units
 */
public record DrawingPart(Shape shape,
                          boolean filled,
                          boolean stroked,
                          float strokeWidth) {
}
