package it.infog.psiche.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileErrorsTest {

    private static final long GIGABYTE = 1024L * 1024 * 1024;

    @TempDir
    Path tempDir;

    @Test
    void aMissingPermissionSuggestsAFolderOfTheAuthorsOwn() {
        assertThat(FileErrors.reason(new AccessDeniedException("C:\\Program Files (x86)\\Psiche\\novel"), tempDir))
                .isEqualTo("Psiche Writer has no permission to write there: choose a folder of your own, such as"
                        + " Documents or your home folder.");
    }

    @Test
    void aReadOnlyOrFullDriveIsToldByTheDriveInAnyLanguage() {
        FileSystemException italian = new FileSystemException("D:\\romanzo", null, "Il supporto è protetto");

        assertThat(FileErrors.reason(italian, Optional.of(new Drive(true, GIGABYTE))))
                .isEqualTo("The drive is read-only: choose a folder of your own, such as Documents or your home"
                        + " folder.");
        assertThat(FileErrors.reason(italian, Optional.of(new Drive(false, 4096))))
                .isEqualTo("The disk is full: free some space, or choose another drive.");
        assertThat(FileErrors.reason(italian, Optional.of(new Drive(false, GIGABYTE))))
                .isEqualTo("The system says: D:\\romanzo: Il supporto è protetto.");
    }

    @Test
    void theSystemsEnglishWordsStillTellADriveThatCannotSay() {
        assertThat(FileErrors.reason(new FileSystemException("/media/usb/novel", null, "Read-only file system"),
                Optional.empty())).startsWith("The drive is read-only: choose a folder of your own");
        assertThat(FileErrors.reason(new FileSystemException("D:\\novel", null, "The media is write protected"),
                Optional.empty())).startsWith("The drive is read-only");
        assertThat(FileErrors.reason(new IOException("No space left on device"), Optional.empty()))
                .isEqualTo("The disk is full: free some space, or choose another drive.");
        assertThat(FileErrors.reason(new FileSystemException("D:\\novel", null,
                "There is not enough space on the disk"), Optional.empty())).startsWith("The disk is full");
    }

    @Test
    void aFolderGoneOrAnyOtherErrorStillGetsASentence() {
        assertThat(FileErrors.reason(new NoSuchFileException(tempDir.resolve("gone/novel").toString()), tempDir))
                .isEqualTo("The folder it goes in is not there any more.");
        assertThat(FileErrors.reason(new IOException("Input/output error"), tempDir))
                .isEqualTo("The system says: Input/output error.");
        assertThat(FileErrors.reason(new IOException(), tempDir))
                .isEqualTo("The system gave no reason (IOException).");
    }

    /** A drive that only says whether it is read-only and how much room it has. */
    private static final class Drive extends FileStore {

        private final boolean readOnly;
        private final long usableSpace;

        Drive(boolean readOnly, long usableSpace) {

            this.readOnly = readOnly;
            this.usableSpace = usableSpace;
        }

        @Override public boolean isReadOnly() { return readOnly; }
        @Override public long getUsableSpace() { return usableSpace; }
        @Override public String name() { return "drive"; }
        @Override public String type() { return "test"; }
        @Override public long getTotalSpace() { return usableSpace; }
        @Override public long getUnallocatedSpace() { return usableSpace; }
        @Override public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) { return false; }
        @Override public boolean supportsFileAttributeView(String name) { return false; }
        @Override public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) { return null; }
        @Override public Object getAttribute(String attribute) { return null; }
    }
}
