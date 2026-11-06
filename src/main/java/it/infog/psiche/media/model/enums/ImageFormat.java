package it.infog.psiche.media.model.enums;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The image formats a chapter can show: those every export packs and the Media tab can draw. WebP and SVG are left
 * out, as Java reads neither, and so is GIF, whose 256 colours print poorly.
 */
public enum ImageFormat {

    JPEG("JPEG", "jpeg", List.of("jpg", "jpeg")),
    PNG("PNG", "png", List.of("png"));

    private final String label;
    private final String readerName;
    private final List<String> extensions;

    ImageFormat(String label, String readerName, List<String> extensions) {

        this.label = label;
        this.readerName = readerName;
        this.extensions = extensions;
    }

    /** The format by the extension of {@code file}'s name, whatever its capitals; empty for any other file. */
    public static Optional<ImageFormat> ofFile(Path file) {

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1);

        return Arrays.stream(values()).filter(format -> format.extensions.contains(extension)).findFirst();
    }

    /** The format Java's image reader named {@code readerName}, such as {@code "jpeg"}; empty for any other. */
    public static Optional<ImageFormat> ofReader(String readerName) {

        return Arrays.stream(values()).filter(format -> format.readerName.equalsIgnoreCase(readerName)).findFirst();
    }

    /** Every extension of every format, such as {@code jpg}, for a file chooser's filter. */
    public static List<String> allExtensions() {

        return Arrays.stream(values()).flatMap(format -> format.extensions.stream()).toList();
    }

    /** The format's name as the author knows it, such as "JPEG". */
    public String label() {

        return label;
    }
}
