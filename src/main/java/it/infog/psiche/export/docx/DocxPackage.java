package it.infog.psiche.export.docx;

import it.infog.psiche.common.Template;
import it.infog.psiche.export.ZipContainer;
import it.infog.psiche.export.document.model.Picture;
import it.infog.psiche.export.docx.model.enums.Part;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The .docx file itself: a zip of the document's parts. On closing it lists every part's content type in
 * {@code [Content_Types].xml}, which a word processor reads to tell them apart: the XML parts by name, the images by
 * their extension.
 */
final class DocxPackage implements AutoCloseable {

    private static final Template CONTENT_TYPES = Template.load(DocxPackage.class, "content-types.xml");
    // The templates explain themselves in XML comments, which the package leaves out, as Word's own files have none.
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->\\s*", Pattern.DOTALL);

    private final ZipContainer zip;
    // Each XML part's path and content type, and each image extension's media type, for the content types.
    private final Map<String, String> overrides = new LinkedHashMap<>();
    private final Map<String, String> defaults = new LinkedHashMap<>();

    /** Creates {@code target}, replacing a file already there. */
    DocxPackage(Path target) throws IOException {

        zip = new ZipContainer(target);
    }

    /** Adds {@code part}, its template filled in as {@code xml}. */
    void add(Part part, String xml) throws IOException {

        addXml(part.path(), xml);
        overrides.put("/" + part.path(), part.contentType());
    }

    /** Copies {@code picture}'s file into the document, as its entry. */
    void addPicture(Picture picture) throws IOException {

        zip.addFile(picture.entry(), picture.file());
        defaults.put(picture.entry().substring(picture.entry().lastIndexOf('.') + 1), picture.mediaType());
    }

    /** Adds the XML part {@code name}, without the comments of its template. */
    private void addXml(String name, String xml) throws IOException {

        zip.addText(name, COMMENT.matcher(xml).replaceAll(""));
    }

    /** Writes the content types of the parts added, and closes the file. */
    @Override
    public void close() throws IOException {

        try {
            StringBuilder types = new StringBuilder();
            defaults.forEach((extension, type) -> types.append(
                    "<Default Extension=\"%s\" ContentType=\"%s\"/>\n".formatted(extension, type)));
            overrides.forEach((name, type) -> types.append(
                    "<Override PartName=\"%s\" ContentType=\"%s\"/>\n".formatted(name, type)));
            addXml("[Content_Types].xml", CONTENT_TYPES.fill(Map.of("types", types.toString())));
        } finally {
            zip.close();
        }
    }
}
