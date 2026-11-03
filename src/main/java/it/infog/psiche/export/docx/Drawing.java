package it.infog.psiche.export.docx;

import it.infog.psiche.common.Template;
import it.infog.psiche.export.XmlText;
import it.infog.psiche.export.document.model.Picture;

import java.util.Map;

/** An image of a DOCX, drawn in the line like a large character, as {@code drawing.xml} writes it. */
final class Drawing {

    // DrawingML measures in English Metric Units, 360000 to the centimetre.
    private static final int EMU_PER_CM = 360_000;
    private static final Template INLINE = Template.load(Drawing.class, "drawing.xml");

    private Drawing() {
    }

    /**
     * The drawing of {@code picture}, for a run.
     *
     * @param number       the drawing's number in the document, from 1, which no other drawing has
     * @param title        the text a screen reader reads for it
     * @param relationship the id the package lists the picture by
     */
    static String inline(Picture picture, int number, String title, String relationship) {

        return INLINE.fill(Map.of(
                "width", String.valueOf(Math.round(picture.width() * EMU_PER_CM)),
                "height", String.valueOf(Math.round(picture.height() * EMU_PER_CM)),
                "number", String.valueOf(number),
                "title", XmlText.escape(title),
                "relationship", relationship));
    }
}
