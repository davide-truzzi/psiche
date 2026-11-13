package it.infog.psiche.export.pdf.model;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * An SVG drawing as Java 2D shapes.
 *
 * @param viewBox the part of the drawing's plane that is shown, from its {@code viewBox}
 * @param parts   its shapes, in the order they are painted
 */
public record Drawing(Rectangle2D viewBox,
                      List<DrawingPart> parts) {
}
