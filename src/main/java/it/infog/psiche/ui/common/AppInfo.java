package it.infog.psiche.ui.common;

import java.lang.module.ModuleDescriptor;
import java.util.Optional;

/** The app's name, as windows and dialogs are titled, and its version, as the build writes it into the module. */
public final class AppInfo {

    public static final String NAME = "Psiche Writer";

    private AppInfo() {
    }

    /**
     * The app's version, from {@code version} in build.gradle.kts, which the build writes into the module, its stage
     * named as people read it: "0.1.0-alpha" shows as "0.1.0 Alpha". None in a run straight from the sources, whose
     * module has no version.
     */
    public static Optional<String> version() {

        return Optional.ofNullable(AppInfo.class.getModule().getDescriptor())
                .flatMap(ModuleDescriptor::rawVersion)
                .map(AppInfo::readable);
    }

    /** {@code version} with its stage after a space and a capital, as "0.1.0 Alpha" for "0.1.0-alpha". */
    static String readable(String version) {

        int dash = version.indexOf('-');
        if (dash < 0 || dash == version.length() - 1)
            return version;

        return version.substring(0, dash) + " " + Character.toUpperCase(version.charAt(dash + 1))
                + version.substring(dash + 2);
    }
}
