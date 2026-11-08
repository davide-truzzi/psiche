package it.infog.psiche.settings;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Where the app's log goes. Run from its package, the app has no console to show it, so the console's error stream,
 * where the log goes along with the traces of crashes and the warnings of JavaFX, is sent to {@value #NAME} in the
 * settings folder instead, where an author can find it and send it; the run before keeps its own as
 * {@value #PREVIOUS}, as a problem is often noticed when the app starts again. Run from the build, it all stays on the
 * console.
 */
public final class LogFile {

    static final String NAME = "psiche.log";
    static final String PREVIOUS = "psiche.previous.log";

    // jpackage's launchers, and psiche.cmd in their place, name the launcher in it; the build does not.
    private static final String LAUNCHER_PROPERTY = "jpackage.app-path";
    // slf4j-simple reads these once, when the first logger is made; it writes to the error stream by default.
    private static final String SHOW_DATE_TIME_PROPERTY = "org.slf4j.simpleLogger.showDateTime";
    private static final String DATE_TIME_FORMAT_PROPERTY = "org.slf4j.simpleLogger.dateTimeFormat";
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    private LogFile() {}

    /**
     * Sends the error stream to the settings folder if the app runs from its package. Must come before anything logs;
     * if the folder cannot be written to, it stays on the console, as nothing can log yet to say so.
     */
    public static void useWhenPackaged() {

        if (System.getProperty(LAUNCHER_PROPERTY) == null)
            return;

        try {
            Path log = keepPrevious(SettingsFolders.resolve().path());
            // Open for the whole run; each line reaches the disk as it is written, so a crash loses none.
            System.setErr(new PrintStream(Files.newOutputStream(log), true, StandardCharsets.UTF_8));
            System.setProperty(SHOW_DATE_TIME_PROPERTY, "true");
            System.setProperty(DATE_TIME_FORMAT_PROPERTY, DATE_TIME_FORMAT);
        } catch (IOException e) {
            // The console it is.
        }
    }

    /**
     * Makes room in {@code folder} for this run's log, keeping the last run's as {@value #PREVIOUS}.
     *
     * @return this run's log file, not there yet
     */
    static Path keepPrevious(Path folder) throws IOException {

        Files.createDirectories(folder);
        Path log = folder.resolve(NAME);
        if (Files.exists(log))
            Files.move(log, folder.resolve(PREVIOUS), StandardCopyOption.REPLACE_EXISTING);

        return log;
    }
}
