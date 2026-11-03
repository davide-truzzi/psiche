package it.infog.psiche.export.docx;

import it.infog.psiche.export.XmlText;
import it.infog.psiche.export.document.model.Picture;

/** An image of a DOCX, drawn in the line like a large character, as WordprocessingML writes one. */
final class Drawing {

    // DrawingML measures in English Metric Units, 360000 to the centimetre.
    private static final int EMU_PER_CM = 360_000;

    private static final String INLINE = """
            <w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="%1$d" cy="%2$d"/>\
            <wp:docPr id="%3$d" name="Picture %3$d" descr="%4$s"/><wp:cNvGraphicFramePr>\
            <a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>\
            <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic>\
            <pic:nvPicPr><pic:cNvPr id="%3$d" name="Picture %3$d" descr="%4$s"/><pic:cNvPicPr/></pic:nvPicPr>\
            <pic:blipFill><a:blip r:embed="%5$s"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>\
            <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="%1$d" cy="%2$d"/></a:xfrm>\
            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>\
            </wp:inline></w:drawing>""";

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

        return INLINE.formatted(Math.round(picture.width() * EMU_PER_CM), Math.round(picture.height() * EMU_PER_CM),
                number, XmlText.escape(title), relationship);
    }
}
