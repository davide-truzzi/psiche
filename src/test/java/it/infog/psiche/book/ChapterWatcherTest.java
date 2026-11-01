package it.infog.psiche.book;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ChapterWatcherTest {

    @TempDir
    Path root;

    @Test
    void reportsChapterCreatedOutsideTheEditor() throws IOException, InterruptedException {
        Files.createDirectories(root.resolve("chapters"));
        Files.createDirectories(root.resolve("media"));
        CountDownLatch changed = new CountDownLatch(1);

        ChapterWatcher watcher;
        try {
            watcher = new ChapterWatcher(BookFolder.open(root), changed::countDown);
        } catch (UncheckedIOException e) {
            // A busy desktop can use up fs.inotify.max_user_instances (128 by default). That is a property of the
            // machine, not of ChapterWatcher, so skip rather than fail; any other error still fails the test.
            assumeFalse(String.valueOf(e.getCause().getMessage()).startsWith("User limit of inotify instances"),
                    "user limit of inotify instances reached");
            throw e;
        }

        try (watcher) {
            Files.writeString(root.resolve("chapters/03-the-storm.md"), "# The storm");

            assertThat(changed.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
