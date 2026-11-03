package it.infog.psiche.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A zip of a document's parts. ODF documents and EPUB books start with their mimetype, stored uncompressed, so other
 * programs can tell their type from the file's first bytes; a DOCX has none.
 */
public final class ZipContainer implements AutoCloseable {

    private final ZipOutputStream zip;

    /** Creates {@code target}, replacing a file already there. */
    public ZipContainer(Path target) throws IOException {

        zip = new ZipOutputStream(Files.newOutputStream(target));
    }

    /**
     * Creates {@code target}, replacing a file already there, and writes {@code mimetype} into it.
     *
     * @param mimetype such as {@code application/epub+zip}
     */
    public ZipContainer(Path target, String mimetype) throws IOException {

        this(target);
        writeMimetype(mimetype);
    }

    /** Adds the text file {@code name}, such as {@code META-INF/container.xml}, in UTF-8. */
    public void addText(String name, String text) throws IOException {

        addBytes(name, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Adds {@code bytes} as the file {@code name}. */
    public void addBytes(String name, byte[] bytes) throws IOException {

        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    /** Copies {@code file} into the zip as {@code name}. */
    public void addFile(String name, Path file) throws IOException {

        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    @Override
    public void close() throws IOException {

        zip.close();
    }

    private void writeMimetype(String mimetype) throws IOException {

        byte[] bytes = mimetype.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(bytes);

        // A stored entry must give its size and checksum before its data.
        ZipEntry entry = new ZipEntry("mimetype");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());

        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
