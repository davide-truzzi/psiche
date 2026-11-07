package it.infog.psiche.settings.model.enums;

/**
 * How the editor shows a chapter's Markdown: in Default, it recedes, its marks faded and its links and images set
 * apart as blocks; in Advanced, it is shown and edited as written. The text is the same in both.
 */
public enum EditorMode {

    DEFAULT("Default"),
    ADVANCED("Advanced");

    private final String label;

    EditorMode(String label) {

        this.label = label;
    }

    /** The name the author picks the mode by. */
    public String label() {

        return label;
    }
}
