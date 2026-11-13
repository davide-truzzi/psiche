package it.infog.psiche.export.pdf;

import it.infog.psiche.export.TitleMark;
import it.infog.psiche.export.pdf.model.Drawing;
import it.infog.psiche.export.pdf.model.DrawingPart;
import com.openhtmltopdf.extend.FSObjectDrawer;
import com.openhtmltopdf.extend.OutputDevice;
import com.openhtmltopdf.render.RenderingContext;
import org.w3c.dom.Element;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.Map;

/**
 * Draws the Psiche mark, {@link TitleMark}, in lines into the PDF, for each {@code <object>} of its type: centred in
 * the box the manuscript's stylesheet gives the object, as large as fits, in the manuscript's black.
 */
final class TitleMarkDrawer implements FSObjectDrawer {

    private final Drawing mark = SvgDrawing.read(TitleMark.svg());

    @Override
    public Map<Shape, String> drawObject(Element element, double x, double y, double width, double height,
                                         OutputDevice device, RenderingContext context, int dotsPerPixel) {

        // The renderer places the object in its dots, and hands over graphics measured in CSS pixels.
        float pixelWidth = (float) (width / dotsPerPixel);
        float pixelHeight = (float) (height / dotsPerPixel);
        device.drawWithGraphics((float) x, (float) y, pixelWidth, pixelHeight,
                graphics -> draw(graphics, pixelWidth, pixelHeight));

        // The mark links nowhere.
        return Map.of();
    }

    private void draw(Graphics2D graphics, double width, double height) {

        //fit the drawing in the box, centred
        Rectangle2D box = mark.viewBox();
        double scale = Math.min(width / box.getWidth(), height / box.getHeight());
        graphics.translate((width - box.getWidth() * scale) / 2, (height - box.getHeight() * scale) / 2);
        graphics.scale(scale, scale);
        graphics.translate(-box.getX(), -box.getY());

        //paint
        graphics.setColor(Color.BLACK);
        for (DrawingPart part : mark.parts()) {
            if (part.filled())
                graphics.fill(part.shape());
            if (part.stroked()) {
                graphics.setStroke(new BasicStroke(part.strokeWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.draw(part.shape());
            }
        }
    }
}
