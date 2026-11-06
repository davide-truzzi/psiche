package it.infog.psiche.markdown.model;

/**
 * A paragraph of a chapter that is one image alone, as Psiche writes them: {@code ![caption](destination)}.
 *
 * @param range       where the paragraph is in the text
 * @param caption     the text between the brackets, which the book prints under the image
 * @param destination where the image is, as written, such as {@code ../media/01-departure/map.png}
 */
public record ImageLine(TextRange range,
                        String caption,
                        String destination) {
}
