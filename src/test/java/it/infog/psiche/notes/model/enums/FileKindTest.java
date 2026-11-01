package it.infog.psiche.notes.model.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileKindTest {

    @ParameterizedTest
    @CsvSource({
            "castello.JPG, IMAGE",
            "map.png, IMAGE",
            "saline.webp, IMAGE",
            "rilievo.TIFF, IMAGE",
            "le-vie-del-sale.pdf, PDF",
            "ponte.heic, OTHER_IMAGE",
            "intervista.docx, DOCUMENT",
            "nonna-ada.m4a, AUDIO",
            "saline.mp4, VIDEO",
            "archivio.tar.gz, ARCHIVE",
            "mappa.kmz, OTHER",
            "README, OTHER",
            ".hidden, OTHER"
    })
    void aFilesKindIsToldByItsExtension(String name, FileKind kind) {
        assertThat(FileKind.of(Path.of(name))).isEqualTo(kind);
    }
}
