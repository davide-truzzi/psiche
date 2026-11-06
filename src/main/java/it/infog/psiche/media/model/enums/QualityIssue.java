package it.infog.psiche.media.model.enums;

/** Why an image will look poor in the exports, told in plain words: no pixels, no dpi. */
public enum QualityIssue {

    TOO_SMALL("Image too small", "It will look blurred in print."),
    TOO_COMPRESSED("Image too compressed", "It will look blocky in print.");

    private final String title;
    private final String detail;

    QualityIssue(String title, String detail) {

        this.title = title;
        this.detail = detail;
    }

    /** What is wrong, as "Image too small". */
    public String title() {

        return title;
    }

    /** What the author will see, as "It will look blurred in print." */
    public String detail() {

        return detail;
    }
}
