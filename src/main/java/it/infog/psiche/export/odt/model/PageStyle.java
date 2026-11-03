package it.infog.psiche.export.odt.model;

/**
 * How an ODT starts a new page. ODF puts a page break, a change of page style or a restart of the page numbers in the
 * style of the first paragraph on the new page, so that paragraph gets an automatic style: its named style plus these.
 *
 * @param prefix              the start of the automatic style's name, such as {@code Chapter}
 * @param masterPage          the page style the new page takes, or empty to keep the one before
 * @param paragraphProperties the paragraph's properties, such as a page break, or empty for none
 */
public record PageStyle(String prefix,
                        String masterPage,
                        String paragraphProperties) {
}
