package it.infog.psiche.settings.model.enums;

/** The tabs beside the open chapter; the one the author pins shows each time a book opens. */
public enum ChapterTab {

    PREVIEW("Preview"),
    NOTES("Notes"),
    MEDIA("Media"),
    HISTORY("History");

    private final String label;

    ChapterTab(String label) {

        this.label = label;
    }

    /** The tab's name, without the count the Notes and Media tabs add. */
    public String label() {

        return label;
    }
}
