package it.infog.psiche.ui.common;

/** The system the app runs on, where its keys differ: macOS has its own ways. */
public final class OperatingSystem {

    private static final boolean MAC = System.getProperty("os.name", "").startsWith("Mac");

    private OperatingSystem() {
    }

    /** True on macOS. */
    public static boolean isMac() {

        return MAC;
    }
}
