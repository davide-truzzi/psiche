package it.infog.psiche.ui.common;

/** The system the app runs on, where its keys and its programs differ: macOS and Windows have their own ways. */
public final class OperatingSystem {

    private static final boolean MAC = System.getProperty("os.name", "").startsWith("Mac");
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private OperatingSystem() {
    }

    /** True on macOS. */
    public static boolean isMac() {

        return MAC;
    }

    /** True on Windows. */
    public static boolean isWindows() {

        return WINDOWS;
    }
}
