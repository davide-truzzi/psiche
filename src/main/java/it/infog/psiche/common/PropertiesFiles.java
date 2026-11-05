package it.infog.psiche.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Properties files as Psiche keeps them: the book's properties, its state, the notes' and the app's settings. Each is
 * written whole through a temporary file, so a crash cannot leave half of one, with its keys in order and no
 * timestamp, so a file changes only when its values do, which keeps Git's history of it quiet.
 */
public final class PropertiesFiles {

    private static final Logger log = LoggerFactory.getLogger(PropertiesFiles.class);

    private PropertiesFiles() {
    }

    /**
     * The properties in {@code file}; none if it does not exist.
     *
     * @throws UncheckedIOException if it cannot be read, or is damaged
     */
    public static Properties read(Path file) {

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        } catch (NoSuchFileException e) {
            // Nothing saved yet; every value has its default.
            return new Properties();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        } catch (IllegalArgumentException e) {
            throw new UncheckedIOException("Cannot read " + file + ": it is damaged", new IOException(e));
        }
    }

    /**
     * The properties in {@code file}; none if it does not exist, or is damaged, which is logged: for files whose
     * values all have defaults.
     *
     * @throws UncheckedIOException if it cannot be read
     */
    public static Properties readLeniently(Path file) {

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return load(reader, file.toString());
        } catch (NoSuchFileException e) {
            return new Properties();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    /** The properties {@code reader} gives, from {@code source}; none, and logged, if they are damaged. */
    public static Properties load(Reader reader, String source) throws IOException {

        Properties properties = new Properties();
        try {
            properties.load(reader);
        } catch (IllegalArgumentException e) {
            log.warn("Reading {} as empty: it is damaged", source, e);
            return new Properties();
        }
        return properties;
    }

    /**
     * Writes {@code properties} to {@code file}, in place of the one there, under {@code comment} if it is not empty.
     *
     * @throws UncheckedIOException if it cannot be written; the file is then as it was
     */
    public static void write(Path file, Properties properties, String comment) {

        StringWriter text = new StringWriter();
        try {
            properties.store(text, null);
            String header = comment.isEmpty() ? "" : "# " + comment + "\n";
            String entries = text.toString().lines()
                    .filter(line -> !line.startsWith("#"))
                    .sorted()
                    .collect(Collectors.joining("\n", header, "\n"));
            SafeFiles.writeString(file, entries);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + file, e);
        }
    }
}
