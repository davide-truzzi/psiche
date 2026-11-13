package it.infog.psiche.ui.common;

import javafx.scene.image.Image;
import javafx.scene.shape.SVGPath;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The app's small drawings, from the SVG files in the {@code icons} resources, where they can be seen and changed in
 * any SVG editor. JavaFX shows no SVG images, so only a file's paths are read, joined into one shape: the style sheet
 * gives it its colour, stroke and size, and those in the file only show it as the app does. A drawing in colours
 * JavaFX cannot give a shape, such as a gradient, is shown as a picture instead: a PNG exported from its SVG, which
 * stays beside it as the source to change it in.
 */
public final class Icons {

    private static final String FOLDER = "/it/infog/psiche/ui/icons/";
    // Each file is read once, the first time its drawing is needed.
    private static final Map<String, String> PATHS = new ConcurrentHashMap<>();

    private Icons() {
    }

    /** The drawing of {@code name}.svg, as a shape of its own or the shape of a region. */
    public static SVGPath shape(String name) {

        SVGPath shape = new SVGPath();
        shape.setContent(pathData(name));
        return shape;
    }

    /**
     * The picture {@code name}.png, to show {@code height} pixels high. It is read at twice that height, so it stays
     * sharp on screens that scale the window up; the PNG is exported at that size at least.
     */
    public static Image picture(String name, double height) {

        String resource = Icons.class.getResource(FOLDER + name + ".png").toExternalForm();
        return new Image(resource, 0, height * 2, true, true);
    }

    /** The paths of {@code name}.svg, joined into one. */
    static String pathData(String name) {

        return PATHS.computeIfAbsent(name, Icons::read);
    }

    private static String read(String name) {

        String resource = FOLDER + name + ".svg";
        List<String> data = new ArrayList<>();
        try (InputStream in = Icons.class.getResourceAsStream(resource)) {
            NodeList paths = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in)
                    .getElementsByTagName("path");
            for (int i = 0; i < paths.getLength(); i++)
                data.add(((Element) paths.item(i)).getAttribute("d"));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read the icon " + resource, e);
        }

        if (data.isEmpty())
            throw new IllegalStateException("The icon " + resource + " has no paths");

        return String.join(" ", data);
    }
}
