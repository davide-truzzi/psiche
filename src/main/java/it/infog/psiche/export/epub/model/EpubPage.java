package it.infog.psiche.export.epub.model;

/**
 * One XHTML page of the EPUB, in reading order.
 *
 * @param id         its id in the package document, such as {@code chapter-3}
 * @param path       where it is packed, from the EPUB's folder, such as {@code xhtml/chapter-3.xhtml}
 * @param title      the title readers and the contents know it by
 * @param xhtml      the page itself
 * @param properties the manifest properties it needs, such as {@code svg} or {@code nav}; empty for none
 */
public record EpubPage(String id,
                       String path,
                       String title,
                       String xhtml,
                       String properties) {
}
