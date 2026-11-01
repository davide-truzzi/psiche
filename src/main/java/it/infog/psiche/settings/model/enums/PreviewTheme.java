package it.infog.psiche.settings.model.enums;

/** How the preview shows the page: as the paper sheet it has always been, or dark, for writing at night. */
public enum PreviewTheme {

    DEFAULT("Default"),
    DARK("Dark");

    private final String label;

    PreviewTheme(String label) {

        this.label = label;
    }

    /** The name the author picks the theme by. */
    public String label() {

        return label;
    }
}
