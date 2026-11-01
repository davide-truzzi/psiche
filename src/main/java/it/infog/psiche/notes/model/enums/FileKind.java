package it.infog.psiche.notes.model.enums;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** What kind of file a note carries, told by its extension, which decides how the note shows it. */
public enum FileKind {

    /** An image the board shows as a picture. */
    IMAGE("png", "jpg", "jpeg", "gif", "bmp", "webp", "tif", "tiff"),
    /** A PDF, shown by its first page. */
    PDF("pdf"),
    /** An image the board cannot draw, shown by an icon. */
    OTHER_IMAGE("heic", "heif", "avif", "svg", "psd", "raw", "cr2", "nef", "dng"),
    DOCUMENT("doc", "docx", "odt", "rtf", "txt", "md", "pages", "epub", "xls", "xlsx", "ods", "ppt", "pptx", "odp",
            "csv"),
    AUDIO("mp3", "m4a", "wav", "flac", "ogg", "opus", "aac", "aiff", "wma"),
    VIDEO("mp4", "mov", "mkv", "avi", "webm", "m4v", "wmv", "mpg", "mpeg"),
    ARCHIVE("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz"),
    OTHER;

    private final Set<String> extensions;

    FileKind(String... extensions) {

        this.extensions = Set.of(extensions);
    }

    /** The kind of {@code file}, by its extension in any letter case; {@link #OTHER} without a known one. */
    public static FileKind of(Path file) {

        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);

        return Arrays.stream(values()).filter(kind -> kind.extensions.contains(extension)).findFirst().orElse(OTHER);
    }

    /** The name the kind is styled by. */
    public String key() {

        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
