package it.infog.psiche.ui.common;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Psiche Writer logo, from the brand kit's SVG files. JavaFX shows no SVG images, so each file's paths and circles
 * become shapes, colored by the style sheet: a stroked shape has the style class {@code logo-stroke}, a filled one
 * {@code logo-fill}. Only what the kit's files use is read: groups, paths, circles, fill, stroke, and the translate
 * and scale transforms.
 */
public final class Logo {

    // Below 32 pixels the brand kit draws the mark without its dots and ring.
    private static final String MARK = "logo/psiche-mark-small.svg";
    private static final String LOCKUP = "logo/psiche-lockup-stacked.svg";
    private static final Pattern TRANSFORM = Pattern.compile("(translate|scale)\\(([^)]*)\\)");

    private Logo() {
    }

    /** The small mark, {@code height} pixels tall. */
    public static Node mark(double height) {

        Group shapes = load(MARK);
        double factor = height / shapes.getLayoutBounds().getHeight();
        shapes.getTransforms().add(new Scale(factor, factor));

        return new Group(shapes);
    }

    /** The whole logo, the mark above the name, {@code height} pixels tall. */
    public static Node lockup(double height) {

        Group shapes = load(LOCKUP);
        double factor = height / shapes.getLayoutBounds().getHeight();
        shapes.getTransforms().add(new Scale(factor, factor));

        return new Group(shapes);
    }

    /** The whole logo, the mark above the name, kept at {@code share} of the width or height of {@code area}. */
    public static Node lockup(Region area, double share) {

        Group shapes = load(LOCKUP);
        Bounds bounds = shapes.getLayoutBounds();
        DoubleBinding factor = Bindings.createDoubleBinding(() -> share * Math.min(
                        area.getWidth() / bounds.getWidth(), area.getHeight() / bounds.getHeight()),
                area.widthProperty(), area.heightProperty());

        Scale scale = new Scale();
        scale.xProperty().bind(factor);
        scale.yProperty().bind(factor);
        shapes.getTransforms().add(scale);

        return new Group(shapes);
    }

    private static Group load(String resource) {

        try (InputStream in = Logo.class.getResourceAsStream(resource)) {
            Element svg = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in).getDocumentElement();
            return group(svg, new Paint("black", "none", 1));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read the logo " + resource, e);
        }
    }

    /** The shapes of {@code element}'s children, which inherit its paint. */
    private static Group group(Element element, Paint inherited) {

        Paint paint = inherited.with(element);
        List<Node> children = new ArrayList<>();
        for (org.w3c.dom.Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element childElement))
                continue;

            Node node = switch (childElement.getTagName()) {
                case "g" -> group(childElement, paint);
                case "path" -> shape(new SVGPath(), childElement, paint);
                case "circle" -> shape(circle(childElement), childElement, paint);
                default -> null;
            };
            if (node != null)
                children.add(node);
        }

        Group group = new Group(children);
        group.getTransforms().addAll(transforms(element.getAttribute("transform")));
        return group;
    }

    private static Circle circle(Element element) {

        return new Circle(number(element, "cx"), number(element, "cy"), number(element, "r"));
    }

    private static Shape shape(Shape shape, Element element, Paint inherited) {

        if (shape instanceof SVGPath path)
            path.setContent(element.getAttribute("d"));

        //paint: the colors themselves come from the style sheet
        Paint paint = inherited.with(element);
        shape.setFill(Color.TRANSPARENT);
        if (!paint.fill().equals("none")) {
            shape.setFill(Color.BLACK);
            shape.getStyleClass().add("logo-fill");
        }
        if (!paint.stroke().equals("none")) {
            shape.setStroke(Color.BLACK);
            shape.setStrokeWidth(paint.strokeWidth());
            shape.setStrokeLineCap(StrokeLineCap.ROUND);
            shape.getStyleClass().add("logo-stroke");
        }

        shape.getTransforms().addAll(transforms(element.getAttribute("transform")));
        return shape;
    }

    private static List<Transform> transforms(String attribute) {

        List<Transform> transforms = new ArrayList<>();
        Matcher matcher = TRANSFORM.matcher(attribute);
        while (matcher.find()) {
            String[] values = matcher.group(2).trim().split("[\\s,]+");
            double x = Double.parseDouble(values[0]);
            double y = values.length > 1 ? Double.parseDouble(values[1]) : matcher.group(1).equals("scale") ? x : 0;
            transforms.add(matcher.group(1).equals("scale") ? new Scale(x, y) : new Translate(x, y));
        }

        return transforms;
    }

    private static double number(Element element, String attribute) {

        return Double.parseDouble(element.getAttribute(attribute));
    }

    /** The fill and stroke an SVG element has, its own or inherited; only whether each is "none" matters. */
    private record Paint(String fill, String stroke, double strokeWidth) {

        Paint with(Element element) {

            return new Paint(
                    element.hasAttribute("fill") ? element.getAttribute("fill") : fill,
                    element.hasAttribute("stroke") ? element.getAttribute("stroke") : stroke,
                    element.hasAttribute("stroke-width") ? number(element, "stroke-width") : strokeWidth);
        }
    }
}
