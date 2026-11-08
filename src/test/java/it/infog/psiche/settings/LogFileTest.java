package it.infog.psiche.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LogFileTest {

    @TempDir
    Path tempDir;

    @Test
    void theFirstRunLogsInTheSettingsFolderItMakes() throws IOException {
        Path folder = tempDir.resolve("config/psiche");

        assertThat(LogFile.keepPrevious(folder)).isEqualTo(folder.resolve(LogFile.NAME)).doesNotExist();
        assertThat(folder).isDirectory();
    }

    @Test
    void eachRunKeepsTheLogOfTheRunBefore() throws IOException {
        Files.writeString(tempDir.resolve(LogFile.PREVIOUS), "two runs ago");
        Files.writeString(tempDir.resolve(LogFile.NAME), "last run");

        Path log = LogFile.keepPrevious(tempDir);

        assertThat(log).doesNotExist();
        assertThat(tempDir.resolve(LogFile.PREVIOUS)).hasContent("last run");
    }
}
