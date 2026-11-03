package it.infog.psiche.export.odt;

import it.infog.psiche.common.Template;
import it.infog.psiche.export.ZipContainer;
import it.infog.psiche.export.document.model.Picture;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The .odt file itself: a zip of the document's parts. It starts with the mimetype, and on closing lists every part
 * added in the manifest, which a word processor reads to find them.
 */
final class OdtPackage implements AutoCloseable {

    static final String MIMETYPE = "application/vnd.oasis.opendocument.text";

    private static final Template MANIFEST = Template.load(OdtPackage.class, "manifest.xml");
    private static final String MANIFEST_ENTRY = """
              <manifest:file-entry manifest:full-path="%s" manifest:media-type="%s"/>
            """;

    private final ZipContainer zip;
    // Each part's path in the zip and its media type, for the manifest.
    private final Map<String, String> parts = new LinkedHashMap<>();

    /** Creates {@code target}, replacing a file already there, and writes the mimetype into it. */
    OdtPackage(Path target) throws IOException {

        zip = new ZipContainer(target, MIMETYPE);
    }

    /** Adds the XML part {@code name}, such as {@code content.xml}. */
    void addXml(String name, String xml) throws IOException {

        zip.addText(name, xml);
        parts.put(name, "text/xml");
    }

    /** Copies {@code picture}'s file into the document, as its entry. */
    void addPicture(Picture picture) throws IOException {

        zip.addFile(picture.entry(), picture.file());
        parts.put(picture.entry(), picture.mediaType());
    }

    /** Writes the manifest of the parts added, and closes the file. */
    @Override
    public void close() throws IOException {

        try {
            StringBuilder entries = new StringBuilder();
            parts.forEach((name, mediaType) -> entries.append(MANIFEST_ENTRY.formatted(name, mediaType)));
            addXml("META-INF/manifest.xml", MANIFEST.fill(Map.of("mimetype", MIMETYPE, "entries", entries.toString())));
        } finally {
            zip.close();
        }
    }
}
