package it.infog.psiche.export.odt.model.enums;

import it.infog.psiche.export.odt.OdtBody;

/**
 * Where the book starts a new page. ODF puts a page break, a change of page style or a restart of the page numbers in
 * the style of the first paragraph on the new page, so {@link OdtBody} gives that paragraph an automatic style: its
 * named style plus the properties below.
 */
public enum PageStart {

    /** The title page: the first page, on the front matter's page style, without the header. */
    TITLE_PAGE("Title_Page", "Front_20_Matter", ""),
    /** The contents: a new page, still on the front matter's page style. */
    CONTENTS("Contents_Page", "Front_20_Matter", ""),
    /** The first chapter: back to the manuscript's page style, with the header, numbered from 1. */
    FIRST_CHAPTER("First_Chapter", "Standard", "style:page-number=\"1\""),
    /** Any later chapter: a page break. */
    CHAPTER("Chapter", "", "fo:break-before=\"page\"");

    private final String prefix;
    private final String masterPage;
    private final String paragraphProperties;

    PageStart(String prefix, String masterPage, String paragraphProperties) {

        this.prefix = prefix;
        this.masterPage = masterPage;
        this.paragraphProperties = paragraphProperties;
    }

    /** The name of the automatic style for {@code style} at this kind of page start, such as {@code Chapter_List}. */
    public String styleName(Style style) {

        return prefix + "_" + style.xmlName();
    }

    /** The automatic style for {@code style} at this kind of page start, for {@code <office:automatic-styles>}. */
    public String automaticStyle(Style style) {

        String master = masterPage.isEmpty() ? "" : " style:master-page-name=\"%s\"".formatted(masterPage);
        String properties = paragraphProperties.isEmpty()
                ? ""
                : "<style:paragraph-properties %s/>".formatted(paragraphProperties);

        return """
                <style:style style:name="%s" style:family="paragraph" style:parent-style-name="%s"%s>%s</style:style>
                """.formatted(styleName(style), style.xmlName(), master, properties);
    }
}
