package it.infog.psiche.ui.notes;

import java.util.Locale;

/** How a note's file is named on its card: its size as people read it, and a long name shortened. */
final class FileLabels {

    // Names longer than this are shortened in the middle, where they differ least.
    private static final int NAME_LENGTH = 28;

    private FileLabels() {
    }

    /** {@code bytes} as people read sizes: "240 KB", "18.4 MB", "1.2 GB". */
    static String readableSize(long bytes) {

        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return Math.round(bytes / 1024.0) + " KB";
        if (bytes < 1024L * 1024 * 1024)
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));

        return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** {@code name}, shortened in the middle to {@value #NAME_LENGTH} characters if longer. */
    static String shortened(String name) {

        if (name.length() <= NAME_LENGTH)
            return name;

        int head = NAME_LENGTH / 2;
        int tail = NAME_LENGTH - head - 1;
        return name.substring(0, head) + "…" + name.substring(name.length() - tail);
    }
}
