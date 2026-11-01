package it.infog.psiche.settings;

import it.infog.psiche.settings.model.SettingsFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsFoldersTest {

    @TempDir
    Path tempDir;

    private final Map<String, String> properties = new HashMap<>(Map.of("user.home", "/home/anna", "os.name", "Linux"));
    private final Map<String, String> environment = new HashMap<>();

    @Test
    void onLinuxTheyFollowXdgConfigHomeOrElseLiveInDotConfig() {
        assertThat(resolve()).isEqualTo(new SettingsFolder(Path.of("/home/anna/.config/psiche"), false));

        environment.put("XDG_CONFIG_HOME", "/home/anna/config");
        assertThat(resolve().path()).isEqualTo(Path.of("/home/anna/config/psiche"));

        // The XDG rules say a relative XDG_CONFIG_HOME is to be ignored.
        environment.put("XDG_CONFIG_HOME", "config");
        assertThat(resolve().path()).isEqualTo(Path.of("/home/anna/.config/psiche"));
    }

    @Test
    void onWindowsTheyLiveInAppData() {
        properties.put("os.name", "Windows 11");
        properties.put("user.home", "/Users/anna");
        assertThat(resolve().path()).isEqualTo(Path.of("/Users/anna/AppData/Roaming/psiche"));

        environment.put("APPDATA", "/Users/anna/Roaming");
        assertThat(resolve().path()).isEqualTo(Path.of("/Users/anna/Roaming/psiche"));
    }

    @Test
    void onMacTheyLiveInApplicationSupport() {
        properties.put("os.name", "Mac OS X");
        properties.put("user.home", "/Users/anna");

        assertThat(resolve().path()).isEqualTo(Path.of("/Users/anna/Library/Application Support/psiche"));
    }

    @Test
    void aSettingsFolderNextToTheLauncherMakesThemPortable() throws IOException {
        Path app = Files.createDirectories(tempDir.resolve("psiche"));
        properties.put("jpackage.app-path", app.resolve("psiche.exe").toString());
        assertThat(resolve().portable()).as("no settings folder yet").isFalse();

        Path portable = Files.createDirectory(app.resolve("settings"));
        assertThat(resolve()).isEqualTo(new SettingsFolder(portable, true));
    }

    @Test
    void anExplicitFolderComesFirst() throws IOException {
        Path app = Files.createDirectories(tempDir.resolve("psiche"));
        Files.createDirectory(app.resolve("settings"));
        properties.put("jpackage.app-path", app.resolve("psiche.exe").toString());
        properties.put("psiche.settings.dir", "/tmp/psiche-test");

        assertThat(resolve()).isEqualTo(new SettingsFolder(Path.of("/tmp/psiche-test"), false));
    }

    private SettingsFolder resolve() {
        return SettingsFolders.resolve(properties::get, environment);
    }
}
