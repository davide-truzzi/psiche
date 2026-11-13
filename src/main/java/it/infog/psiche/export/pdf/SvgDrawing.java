package it.infog.psiche.export.pdf;

import it.infog.psiche.export.pdf.model.Drawing;
import it.infog.psiche.export.pdf.model.DrawingPart;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Reads a small SVG drawing as Java 2D shapes, so a PDF can draw it in lines. Only what the brand kit's mark uses is
 * read: groups, paths with absolute commands (M, L, H, V, C, Z), circles, and their fill, stroke and stroke width,
 * inherited from the groups; a stroke always has round ends.
 */
final class SvgDrawing {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");
    private static final String NONE = "none";

    /** What an element paints with, its own or inherited. */
    private record Paint(boolean fill,
                         boolean stroke,
                         float strokeWidth) {

        Paint with(Element element) {

            return new Paint(
                    element.hasAttribute("fill") ? !element.getAttribute("fill").equals(NONE) : fill,
                    element.hasAttribute("stroke") ? !element.getAttribute("stroke").equals(NONE) : stroke,
                    element.hasAttribute("stroke-width") ? number(element, "stroke-width") : strokeWidth);
        }
    }

    private SvgDrawing() {
    }

    /** The drawing {@code svg}, the text of an SVG file. */
    static Drawing read(String svg) {

        Element root;
        try {
            root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read the drawing", e);
        }

        double[] box = Arrays.stream(root.getAttribute("viewBox").trim().split("[\\s,]+"))
                .mapToDouble(Double::parseDouble).toArray();
        List<DrawingPart> parts = new ArrayList<>();
        // As SVG paints: black inside, no outline, one unit wide.
        collect(root, new Paint(true, false, 1), parts);

        return new Drawing(new Rectangle2D.Double(box[0], box[1], box[2], box[3]), List.copyOf(parts));
    }

    /** A path's {@code d} as a Java 2D path. */
    static Path2D path(String data) {

        Deque<String> tokens = new ArrayDeque<>(TOKEN.matcher(data).results().map(MatchResult::group).toList());
        Path2D path = new Path2D.Double();
        char command = 0;
        while (!tokens.isEmpty()) {
            if (Character.isLetter(tokens.peek().charAt(0)))
                command = tokens.pop().charAt(0);

            Point2D at = path.getCurrentPoint();
            switch (command) {
                case 'M' -> {
                    path.moveTo(next(tokens), next(tokens));
                    // More points after a move are lines, as SVG reads them.
                    command = 'L';
                }
                case 'L' -> path.lineTo(next(tokens), next(tokens));
                case 'H' -> path.lineTo(next(tokens), at.getY());
                case 'V' -> path.lineTo(at.getX(), next(tokens));
                case 'C' -> path.curveTo(next(tokens), next(tokens), next(tokens), next(tokens), next(tokens),
                        next(tokens));
                case 'Z' -> {
                    path.closePath();
                    // Only a new command may follow.
                    command = 0;
                }
                default -> throw new IllegalArgumentException("Cannot read the path command '" + command + "' in "
                        + data);
            }
        }

        return path;
    }

    /** Adds the shapes of {@code element}'s children to {@code parts}, painted as {@code inherited} says. */
    private static void collect(Element element, Paint inherited, List<DrawingPart> parts) {

        Paint paint = inherited.with(element);
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element childElement))
                continue;

            switch (childElement.getTagName()) {
                case "g" -> collect(childElement, paint, parts);
                case "path" -> parts.add(part(path(childElement.getAttribute("d")), paint.with(childElement)));
                case "circle" -> parts.add(part(circle(childElement), paint.with(childElement)));
                default -> {}
            }
        }
    }

    private static DrawingPart part(Shape shape, Paint paint) {

        return new DrawingPart(shape, paint.fill(), paint.stroke(), paint.strokeWidth());
    }

    private static Shape circle(Element element) {

        double radius = number(element, "r");
        return new Ellipse2D.Double(number(element, "cx") - radius, number(element, "cy") - radius, 2 * radius,
                2 * radius);
    }

    private static double next(Deque<String> tokens) {

        if (tokens.isEmpty() || Character.isLetter(tokens.peek().charAt(0)))
            throw new IllegalArgumentException("A path command lacks a number");

        return Double.parseDouble(tokens.pop());
    }

    private static float number(Element element, String attribute) {

        return Float.parseFloat(element.getAttribute(attribute));
    }
}
