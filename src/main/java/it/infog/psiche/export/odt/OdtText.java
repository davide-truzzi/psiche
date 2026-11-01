package it.infog.psiche.export.odt;

/** Text as the document's XML holds it: escaped, and with its runs of spaces and its tabs kept. */
final class OdtText {

    // Characters below the space that XML 1.0 does not allow, even escaped; tab and line ends are allowed.
    private static final String NOT_IN_XML = "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]";

    private OdtText() {
    }

    /** {@code text} escaped for an XML element or a double-quoted attribute, without the characters XML forbids. */
    static String escape(String text) {

        return text.replaceAll(NOT_IN_XML, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Appends {@code text}, escaped, to {@code xml}. A word processor collapses a run of spaces into one, as a browser
     * does, so the first space of a run is written as it is and the others as {@code <text:s>}; a tab is written as
     * {@code <text:tab/>}.
     *
     * @param lineStart true if {@code text} starts a line of code, whose leading spaces would otherwise be dropped,
     *                  so all of them are written as {@code <text:s>}
     */
    static void append(StringBuilder xml, String text, boolean lineStart) {

        String escaped = escape(text);
        int i = 0;
        while (i < escaped.length()) {
            char c = escaped.charAt(i);

            if (c == '\t') {
                xml.append("<text:tab/>");
                i++;
            } else if (c != ' ') {
                xml.append(c);
                i++;
            } else {
                int end = runOfSpacesEnd(escaped, i);
                appendSpaces(xml, end - i, lineStart && i == 0);
                i = end;
            }
        }
    }

    /** Where the run of spaces starting at {@code start} ends. */
    private static int runOfSpacesEnd(String text, int start) {

        int end = start;
        while (end < text.length() && text.charAt(end) == ' ')
            end++;

        return end;
    }

    /** Appends {@code count} spaces that all show: a plain one, unless at a line start, and the rest as elements. */
    private static void appendSpaces(StringBuilder xml, int count, boolean atLineStart) {

        int counted = atLineStart ? count : count - 1;
        if (!atLineStart)
            xml.append(' ');

        if (counted == 1)
            xml.append("<text:s/>");
        else if (counted > 1)
            xml.append("<text:s text:c=\"").append(counted).append("\"/>");
    }
}
