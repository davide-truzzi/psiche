package it.infog.psiche.export.document.model;

import java.nio.file.Path;

/**
 * An image of the chapter, packed into the document.
 *
 * @param file      the image on disk
 * @param entry     where it is packed in the document, such as {@code Pictures/image1.png}
 * @param mediaType its media type, such as {@code image/png}
 * @param width     how wide it is drawn, in centimetres
 * @param height    how tall it is drawn, in centimetres
 */
public record Picture(Path file,
                      String entry,
                      String mediaType,
                      double width,
                      double height) {
}
