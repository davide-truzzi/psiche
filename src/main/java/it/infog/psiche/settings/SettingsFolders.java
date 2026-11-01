package it.infog.psiche.settings;

import it.infog.psiche.settings.model.SettingsFolder;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Finds the folder the app keeps its settings in. It is never inside the app itself, which an AppImage keeps read-only
 * and Windows keeps in Program Files when installed there. The first of these that applies:
 * <ol>
 *     <li>the folder the system property {@value #FOLDER_PROPERTY} names, for tests and development;</li>
 *     <li>a {@code settings} folder next to the app's launcher, if the author made one and it can be written to: the
 *     portable mode, for an app unzipped on a USB stick. jpackage's launchers name themselves in the system property
 *     {@code jpackage.app-path};</li>
 *     <li>the operating system's folder for settings, with {@value #NAME} in it: {@code $XDG_CONFIG_HOME/psiche} on
 *     Linux, by default {@code ~/.config/psiche}; {@code %APPDATA%\psiche} on Windows;
 *     {@code ~/Library/Application Support/psiche} on macOS.</li>
 * </ol>
 * An AppImage needs nothing of its own: it leaves the environment as it is, and in its portable mode, with a
 * {@code psiche.AppImage.config} folder next to it, it sets {@code XDG_CONFIG_HOME} to that folder.
 */
public final class SettingsFolders {

    static final String NAME = "psiche";
    static final String FOLDER_PROPERTY = "psiche.settings.dir";
    static final String PORTABLE_FOLDER = "settings";

    private SettingsFolders() {
    }

    /** The settings folder for this run of the app, from its system properties and environment. */
    public static SettingsFolder resolve() {

        return resolve(System::getProperty, System.getenv());
    }

    /**
     * @param properties the system properties by name, such as {@code os.name} and {@code user.home}
     * @param environment the environment variables, such as {@code XDG_CONFIG_HOME} and {@code APPDATA}
     */
    static SettingsFolder resolve(UnaryOperator<String> properties, Map<String, String> environment) {

        String explicit = properties.apply(FOLDER_PROPERTY);
        if (explicit != null && !explicit.isBlank())
            return new SettingsFolder(Path.of(explicit), false);

        //portable mode: a settings folder the author put next to the launcher
        String launcher = properties.apply("jpackage.app-path");
        if (launcher != null && !launcher.isBlank()) {
            Path portable = Path.of(launcher).toAbsolutePath().getParent().resolve(PORTABLE_FOLDER);
            if (Files.isDirectory(portable) && Files.isWritable(portable))
                return new SettingsFolder(portable, true);
        }

        //the operating system's folder
        Path home = Path.of(properties.apply("user.home"));
        String os = properties.apply("os.name").toLowerCase(Locale.ROOT);
        Path base;
        if (os.startsWith("windows"))
            base = absolute(environment.get("APPDATA")).orElse(home.resolve("AppData").resolve("Roaming"));
        else if (os.startsWith("mac"))
            base = home.resolve("Library").resolve("Application Support");
        else
            base = absolute(environment.get("XDG_CONFIG_HOME")).orElse(home.resolve(".config"));

        return new SettingsFolder(base.resolve(NAME), false);
    }

    /** {@code path} if it names an absolute path; the XDG rules say to ignore a relative one. */
    private static Optional<Path> absolute(String path) {

        if (path == null || path.isBlank())
            return Optional.empty();

        try {
            Path folder = Path.of(path);
            return folder.isAbsolute() ? Optional.of(folder) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}
