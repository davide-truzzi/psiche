package it.infog.psiche.notes;

import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NoteLayout;
import it.infog.psiche.notes.model.NotePin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * A note's two fingerprints, which an import compares to tell which side changed it since the last export or import
 * here: one of its content, the text and the file's name and size (hashing whole multi-GB files at every export would
 * be too slow), and one of its place, size, colour and pin. The stacking order is left out: bringing a note to the
 * front by a click is no change to carry over.
 */
final class NotePrints {

    /** Where a note keeps the fingerprint of its content at its last export or import here. */
    static final String CONTENT_KEY = "contentPrint";
    /** Where a note keeps the fingerprint of its place, size, colour and pin at its last export or import here. */
    static final String LAYOUT_KEY = "layoutPrint";

    // A file named but gone from the note's folder.
    private static final long MISSING = -1;

    private NotePrints() {
    }

    /** The fingerprint of {@code note}'s text and file as they are on disk. */
    static String content(Note note) {

        Optional<Path> file = note.attachment();
        return content(note.text(), file.map(path -> path.getFileName().toString()), file.map(NotePrints::size)
                .orElse(MISSING));
    }

    /** The fingerprint of a text and the name and size of a file, {@code size} -1 if it is gone. */
    static String content(String text, Optional<String> file, long size) {

        return hash(text + '\0' + file.orElse("") + '\0' + (file.isPresent() ? size : MISSING));
    }

    /** The fingerprint of {@code note}'s place, size, colour and pin. */
    static String layout(Note note) {

        return layout(note.layout(), note.pin());
    }

    /**
     * The fingerprint of a note's place, size, colour and pin. An unpinned note's is the one it had before notes
     * could be pinned, so pins alone make no note look changed since its last export or import.
     */
    static String layout(NoteLayout layout, Optional<NotePin> pin) {

        String pinned = pin.map(each -> "," + each.paragraph() + "," + each.text()).orElse("");
        return hash(layout.x() + "," + layout.y() + "," + layout.width() + "," + layout.height() + ","
                + layout.color().key() + pinned);
    }

    private static long size(Path file) {

        try {
            return Files.exists(file) ? Files.size(file) : MISSING;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the size of " + file, e);
        }
    }

    /** The first 16 bytes of {@code value}'s SHA-256, in hex: enough to tell two versions of a note apart. */
    private static String hash(String value) {

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }
}
