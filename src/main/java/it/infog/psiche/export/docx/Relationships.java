package it.infog.psiche.export.docx;

import it.infog.psiche.export.XmlText;
import it.infog.psiche.export.document.model.Picture;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the text of a DOCX refers to by id, for {@code word/_rels/document.xml.rels}: its images and the addresses of
 * its links. The parts every DOCX has, such as the styles, are listed in the template with ids of their own.
 */
final class Relationships {

    private static final String IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";
    private static final String HYPERLINK =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink";
    // The text's folder in the package, which the images' targets are relative to.
    private static final String TEXT_FOLDER = "word/";

    // Each relationship's id by its type and target, so an image or address used twice is listed once.
    private final Map<String, String> ids = new LinkedHashMap<>();
    private final StringBuilder xml = new StringBuilder();

    /** The id of {@code picture}, packed in the text's folder. */
    String picture(Picture picture) {

        return add(IMAGE, picture.entry().substring(TEXT_FOLDER.length()), "");
    }

    /** The id of the web or mail {@code address}, outside the package. */
    String link(String address) {

        return add(HYPERLINK, address, " TargetMode=\"External\"");
    }

    /** The relationships added so far, for the template. */
    String xml() {

        return xml.toString();
    }

    private String add(String type, String target, String mode) {

        String key = type + " " + target;
        if (ids.containsKey(key))
            return ids.get(key);

        String id = "rId" + (ids.size() + 1);
        ids.put(key, id);
        xml.append("<Relationship Id=\"%s\" Type=\"%s\" Target=\"%s\"%s/>\n"
                .formatted(id, type, XmlText.escape(target), mode));

        return id;
    }
}
