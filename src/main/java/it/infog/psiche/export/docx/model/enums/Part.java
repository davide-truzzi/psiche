package it.infog.psiche.export.docx.model.enums;

/**
 * The XML parts of every DOCX, each filled in from the template of the same name next to {@code DocxWriter}. The images
 * are parts too, one per picture the text shows.
 */
public enum Part {

    /** Where a word processor starts: the text, and the file's properties. */
    PACKAGE_RELATIONSHIPS("_rels/.rels", "rels.xml", "application/vnd.openxmlformats-package.relationships+xml"),
    /** The text: a chapter, or the whole book. */
    DOCUMENT("word/document.xml", "document.xml",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"),
    /** What the text refers to by id: the parts below, its images and its links. */
    DOCUMENT_RELATIONSHIPS("word/_rels/document.xml.rels", "document.xml.rels",
            "application/vnd.openxmlformats-package.relationships+xml"),
    STYLES("word/styles.xml", "styles.xml",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"),
    /** The bullets and numbers of the lists. */
    NUMBERING("word/numbering.xml", "numbering.xml",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"),
    SETTINGS("word/settings.xml", "settings.xml",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"),
    /** The page header, with the authors, the book's title and the page number. */
    HEADER("word/header1.xml", "header.xml",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"),
    /** The title, authors and language a word processor shows in the file's properties. */
    CORE_PROPERTIES("docProps/core.xml", "core.xml", "application/vnd.openxmlformats-package.core-properties+xml"),
    /** The program that made the file. */
    APP_PROPERTIES("docProps/app.xml", "app.xml",
            "application/vnd.openxmlformats-officedocument.extended-properties+xml");

    private final String path;
    private final String template;
    private final String contentType;

    Part(String path, String template, String contentType) {

        this.path = path;
        this.template = template;
        this.contentType = contentType;
    }

    /** Where the part is in the package. */
    public String path() {

        return path;
    }

    /** The template's file name. */
    public String template() {

        return template;
    }

    public String contentType() {

        return contentType;
    }
}
