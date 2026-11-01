package it.infog.psiche.export.epub.model;

import java.nio.file.Path;

/**
 * An image packed into the EPUB.
 *
 * @param source    the image on disk
 * @param path      where it is packed, from the EPUB's folder, such as {@code images/image-1.png}
 * @param mediaType such as {@code image/png}
 * @param width     its width in pixels once packed; 0 for an image Java cannot measure, such as SVG
 * @param height    its height in pixels once packed, likewise
 * @param scaled    true if it is packed smaller than {@code source}, to stay within what stores accept
 */
public record EpubImage(Path source,
                        String path,
                        String mediaType,
                        int width,
                        int height,
                        boolean scaled) {
}
