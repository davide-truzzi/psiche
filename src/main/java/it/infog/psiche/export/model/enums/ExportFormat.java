package it.infog.psiche.export.model.enums;

/** The formats a chapter or the whole book can be exported to. */
public enum ExportFormat {

    PDF("PDF", "pdf"),
    ODT("ODT", "odt"),
    MARKDOWN("Markdown", "md"),
    /** The book as an EPUB 3 for Apple Books, Kobo, Google Play Books and the distributors. */
    EPUB("EPUB", "epub"),
    /** The book as an EPUB 3 for Amazon Kindle, which adds the cover itself. */
    EPUB_KINDLE("EPUB for Kindle", "epub");

    private final String label;
    private final String extension;

    ExportFormat(String label, String extension) {

        this.label = label;
        this.extension = extension;
    }

    /** The name the author picks the format by. */
    public String label() {

        return label;
    }

    /** The file extension, without the dot. */
    public String extension() {

        return extension;
    }
}
