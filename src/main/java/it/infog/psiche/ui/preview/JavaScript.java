package it.infog.psiche.ui.preview;

/** Writing Java values into the JavaScript the preview runs on its page. */
final class JavaScript {

    private JavaScript() {
    }

    /** {@code text} as a JavaScript string, in double quotes, with what would end or break it escaped. */
    static String string(String text) {

        StringBuilder js = new StringBuilder(text.length() + 16).append('"');
        for (char character : text.toCharArray()) {
            switch (character) {
                case '\\' -> js.append("\\\\");
                case '"' -> js.append("\\\"");
                case '\n' -> js.append("\\n");
                case '\r' -> js.append("\\r");
                case ' ' -> js.append("\\u2028");
                case ' ' -> js.append("\\u2029");
                default -> js.append(character);
            }
        }
        return js.append('"').toString();
    }
}
