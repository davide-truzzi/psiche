package it.infog.psiche.ui.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Shows a file in the system's file manager, its folder open and the file selected: Explorer on Windows, Finder on
 * macOS, and on Linux the file manager the desktop names on D-Bus, as Dolphin or Files; a desktop without one opens
 * the folder alone. The file manager is started on a thread of its own, so the window never waits for it.
 */
public final class FileManager {

    private static final Logger log = LoggerFactory.getLogger(FileManager.class);

    private FileManager() {
    }

    /** Opens the folder holding {@code file}, with the file selected where the system can. */
    public static void reveal(Path file) {

        Path absolute = file.toAbsolutePath();
        Thread.ofVirtual().name("reveal-file").start(() -> {
            try {
                if (!run(revealCommand(absolute)) && !run(openCommand(absolute.getParent())))
                    log.warn("Cannot show {} in the file manager", absolute);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** The command that shows {@code file} selected in its folder. */
    private static List<String> revealCommand(Path file) {

        if (OperatingSystem.isWindows())
            return List.of("explorer.exe", "/select," + file);
        if (OperatingSystem.isMac())
            return List.of("open", "-R", file.toString());

        // dbus-send splits an array's items at commas, so a comma in the name goes percent-encoded, as URIs allow.
        String uri = file.toUri().toString().replace(",", "%2C");
        return List.of("dbus-send", "--session", "--print-reply", "--dest=org.freedesktop.FileManager1",
                "/org/freedesktop/FileManager1", "org.freedesktop.FileManager1.ShowItems", "array:string:" + uri,
                "string:");
    }

    /** The command that opens {@code folder}, for a system that cannot select a file in it. */
    private static List<String> openCommand(Path folder) {

        if (OperatingSystem.isWindows())
            return List.of("explorer.exe", folder.toString());
        if (OperatingSystem.isMac())
            return List.of("open", folder.toString());

        return List.of("xdg-open", folder.toString());
    }

    /**
     * Runs {@code command} and waits for it. Explorer reports failure even when it opens, so on Windows the command
     * counts as done once it starts.
     *
     * @return false if it could not be run or failed, as on a Linux desktop with no file manager on D-Bus
     */
    private static boolean run(List<String> command) throws InterruptedException {

        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return OperatingSystem.isWindows() || process.waitFor() == 0;
        } catch (IOException e) {
            log.debug("Cannot run {}", command.getFirst(), e);
            return false;
        }
    }
}
