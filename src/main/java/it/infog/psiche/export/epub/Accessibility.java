package it.infog.psiche.export.epub;

/**
 * The accessibility metadata EPUB Accessibility 1.1 asks of every EPUB, which stores show readers and the European
 * Accessibility Act expects: how the book can be read ({@code accessMode}), what helps reading it
 * ({@code accessibilityFeature}), what could harm ({@code accessibilityHazard}) and a summary.
 * <p>
 * It only states what Psiche Writer knows of the book. It claims no conformance ({@code dcterms:conformsTo}), which needs
 * someone to evaluate the content.
 */
final class Accessibility {

    private Accessibility() {
    }

    /**
     * The metadata, as {@code <meta>} elements of the package document.
     *
     * @param visual    true if the book has images, the cover included
     * @param described true if every image has a text description
     */
    static String metadata(boolean visual, boolean described) {

        StringBuilder meta = new StringBuilder();

        //how it can be read: by text alone unless images go undescribed
        meta.append(property("schema:accessMode", "textual"));
        if (visual) {
            meta.append(property("schema:accessMode", "visual"));
            meta.append(property("schema:accessModeSufficient", "textual,visual"));
        }
        if (!visual || described)
            meta.append(property("schema:accessModeSufficient", "textual"));

        //what helps: chapters as headings, the contents and landmarks, one reading order, described images
        meta.append(property("schema:accessibilityFeature", "structuralNavigation"));
        meta.append(property("schema:accessibilityFeature", "tableOfContents"));
        meta.append(property("schema:accessibilityFeature", "readingOrder"));
        if (visual && described)
            meta.append(property("schema:accessibilityFeature", "alternativeText"));

        meta.append(property("schema:accessibilityHazard", "none"));
        meta.append(property("schema:accessibilitySummary", summary(visual, described)));

        return meta.toString();
    }

    private static String summary(boolean visual, boolean described) {

        String summary = "This book can be read as text, with a table of contents, chapter headings and a logical "
                + "reading order.";
        if (visual && described)
            return summary + " Every image has a text description.";
        if (visual)
            return summary + " Some images have no text description.";

        return summary;
    }

    private static String property(String name, String value) {

        return "        <meta property=\"%s\">%s</meta>\n".formatted(name, value);
    }
}
