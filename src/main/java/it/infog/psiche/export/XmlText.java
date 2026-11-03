package it.infog.psiche.export;

/** Text as an exported file's XML holds it, for every format: the ODT and DOCX, the PDF's XHTML and the EPUB. */
public final class XmlText {

    // Characters below the space that XML 1.0 does not allow, even escaped; tab and line ends are allowed.
    private static final String NOT_IN_XML = "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]";

    private XmlText() {
    }

    /** {@code text} escaped for an XML element or a double-quoted attribute, without the characters XML forbids. */
    public static String escape(String text) {

        return text.replaceAll(NOT_IN_XML, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
