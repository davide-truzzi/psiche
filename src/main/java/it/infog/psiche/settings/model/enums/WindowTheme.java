package it.infog.psiche.settings.model.enums;

/**
 * How the window looks around the page: the night desk it has always been, or with high contrast, its borders and
 * grey text brighter, for a dim screen or weak sight.
 */
public enum WindowTheme {

    DEFAULT("Default"),
    HIGH_CONTRAST("High contrast");

    private final String label;

    WindowTheme(String label) {

        this.label = label;
    }

    /** The name the author picks the theme by. */
    public String label() {

        return label;
    }
}
