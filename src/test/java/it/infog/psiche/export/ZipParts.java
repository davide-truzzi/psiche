package it.infog.psiche.export;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/** Reads the parts of an exported ODT, DOCX or EPUB, which are all zips of XML parts, for the tests. */
final class ZipParts {

    private ZipParts() {
    }

    /** The text of the part {@code name} in the zip {@code file}. */
    static String entry(Path file, String name) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile()); InputStream in = zip.getInputStream(zip.getEntry(name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Parses with namespaces, so an attribute whose prefix the part does not declare fails the test. */
    static void parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
