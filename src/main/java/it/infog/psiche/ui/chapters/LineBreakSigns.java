package it.infog.psiche.ui.chapters;

/**
 * A text with its line breaks shown as ↵, the Enter key's sign, as a one-line field such as the find bar's shows it,
 * and back: a field drops line breaks, while a search can look for them.
 */
final class LineBreakSigns {

    static final String SIGN = "↵";

    private LineBreakSigns() {
    }

    /** {@code text} with each line ending, however written, as ↵. */
    static String shown(String text) {

        return text.replace("\r\n", SIGN).replace("\r", SIGN).replace("\n", SIGN);
    }

    /** {@code shown}, as a field shows it, with each ↵ a line break, {@code \n}. */
    static String text(String shown) {

        return shown.replace(SIGN, "\n");
    }
}
