package it.infog.psiche.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertiesFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void aFileIsWrittenWholeWithItsKeysInOrderAndNoTimestamp() throws IOException {
        Path file = tempDir.resolve("book.properties");
        Properties properties = new Properties();
        properties.setProperty("title", "La via del sale");
        properties.setProperty("authors", "Anna\nMarco");
        properties.setProperty("language", "it-IT");

        PropertiesFiles.write(file, properties, "Book properties");

        assertThat(file).hasContent("""
                # Book properties
                authors=Anna\\nMarco
                language=it-IT
                title=La via del sale""");
        assertThat(PropertiesFiles.read(file)).isEqualTo(properties);
        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files).containsExactly(file);
        }
    }

    @Test
    void withoutACommentTheFileHoldsOnlyItsValues() throws IOException {
        Path file = tempDir.resolve("note.properties");
        Properties properties = new Properties();
        properties.setProperty("x", "12");

        PropertiesFiles.write(file, properties, "");

        assertThat(file).hasContent("x=12");
    }

    @Test
    void aMissingFileHoldsNothing() {
        Path missing = tempDir.resolve("missing.properties");

        assertThat(PropertiesFiles.read(missing)).isEmpty();
        assertThat(PropertiesFiles.readLeniently(missing)).isEmpty();
    }

    @Test
    void aDamagedFileIsRefusedUnlessReadLeniently() throws IOException {
        Path damaged = Files.writeString(tempDir.resolve("damaged.properties"), "title=\\u12zz\n");

        assertThatThrownBy(() -> PropertiesFiles.read(damaged))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("Cannot read " + damaged + ": it is damaged");
        assertThat(PropertiesFiles.readLeniently(damaged)).isEmpty();
    }

    @Test
    void aFileThatCannotBeWrittenStaysAsItWas() throws IOException {
        Path file = Files.writeString(tempDir.resolve("board.properties"), "zoom=1\n");
        Properties properties = new Properties();
        properties.setProperty("zoom", "2");

        assertThatThrownBy(() -> PropertiesFiles.write(tempDir.resolve("gone/board.properties"), properties, ""))
                .isInstanceOf(UncheckedIOException.class);
        assertThat(file).hasContent("zoom=1");
    }
}
