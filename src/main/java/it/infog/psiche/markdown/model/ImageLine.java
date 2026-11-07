package it.infog.psiche.markdown.model;

import java.util.List;

/**
 * A paragraph of a chapter that is one image alone, as Psiche writes them: {@code ![caption](destination)}.
 *
 * @param range       where the paragraph is in the text
 * @param label       where the caption is written, between the brackets, with its formatting and escapes
 * @param caption     the text between the brackets, which the book prints under the image
 * @param destination where the image is, as written, such as {@code ../media/01-departure/map.png}
 */
public record ImageLine(TextRange range,
                        TextRange label,
                        String caption,
                        String destination) {

    /**
     * Where the Markdown around the caption is written: from the paragraph's start to the caption, {@code ![}, and
     * from the bracket closing it to the paragraph's end, {@code ](destination)}, with the spaces around them.
     */
    public List<TextRange> markers() {

        return List.of(new TextRange(range.start(), label.start()), new TextRange(label.end(), range.end()));
    }
}
