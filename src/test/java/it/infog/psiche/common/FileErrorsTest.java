package it.infog.psiche.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;

import static org.assertj.core.api.Assertions.assertThat;

class FileErrorsTest {

    @Test
    void aMissingPermissionSuggestsAFolderOfTheAuthorsOwn() {
        assertThat(FileErrors.reason(new AccessDeniedException("C:\\Program Files (x86)\\Psiche\\novel")))
                .isEqualTo("Psiche Writer has no permission to write there: choose a folder of your own, such as"
                        + " Documents or your home folder.");
    }

    @Test
    void aReadOnlyOrFullDriveIsNamedAsSuch() {
        assertThat(FileErrors.reason(new FileSystemException("/media/usb/novel", null, "Read-only file system")))
                .startsWith("The drive is read-only: choose a folder of your own");
        assertThat(FileErrors.reason(new FileSystemException("D:\\novel", null, "The media is write protected")))
                .startsWith("The drive is read-only");
        assertThat(FileErrors.reason(new IOException("No space left on device")))
                .isEqualTo("The disk is full: free some space, or choose another drive.");
        FileSystemException windowsFull = new FileSystemException("D:\\novel", null,
                "There is not enough space on the disk");
        assertThat(FileErrors.reason(windowsFull)).startsWith("The disk is full");
    }

    @Test
    void aFolderGoneOrAnyOtherErrorStillGetsASentence() {
        assertThat(FileErrors.reason(new NoSuchFileException("/books/novel")))
                .isEqualTo("The folder it goes in is not there any more.");
        assertThat(FileErrors.reason(new IOException("Input/output error")))
                .isEqualTo("The system says: Input/output error.");
        assertThat(FileErrors.reason(new IOException())).isEqualTo("The system gave no reason (IOException).");
    }
}
