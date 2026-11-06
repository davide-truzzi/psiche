package it.infog.psiche.media.model;

import it.infog.psiche.media.model.enums.ImageFormat;
import it.infog.psiche.media.model.enums.QualityIssue;

import java.nio.file.Path;
import java.util.Set;

/**
 * An image file as the Media tab and the Insert image dialog describe it: its format, read from its content, its size
 * in pixels and in bytes, and what will make it look poor in the exports, none if nothing.
 */
public record MediaImage(Path file,
                         ImageFormat format,
                         int width,
                         int height,
                         long bytes,
                         Set<QualityIssue> issues) {

    /** The file's name, as {@code map.png}. */
    public String name() {

        return file.getFileName().toString();
    }

    /** True if the image will look poor in the exports. */
    public boolean poor() {

        return !issues.isEmpty();
    }
}
