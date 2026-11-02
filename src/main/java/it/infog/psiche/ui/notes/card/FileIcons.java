package it.infog.psiche.ui.notes.card;

import it.infog.psiche.notes.model.enums.FileKind;
import it.infog.psiche.ui.common.Icons;

import javafx.scene.shape.SVGPath;

import java.util.Map;

/**
 * Line drawings of the kinds of file a note can carry, the {@code file-*.svg} icons, in the stroke of the Psiche mark,
 * 30 pixels square. The stylesheet gives them their ink.
 */
final class FileIcons {

    private static final Map<FileKind, String> ICONS = Map.of(
            FileKind.IMAGE, "file-image",
            FileKind.PDF, "file-pdf",
            FileKind.OTHER_IMAGE, "file-image",
            FileKind.DOCUMENT, "file-document",
            FileKind.AUDIO, "file-audio",
            FileKind.VIDEO, "file-video",
            FileKind.ARCHIVE, "file-archive",
            FileKind.OTHER, "file-other");

    private FileIcons() {
    }

    static SVGPath of(FileKind kind) {

        return icon(ICONS.get(kind));
    }

    /** A page crossed out: the note's file is gone from its folder. */
    static SVGPath missing() {

        SVGPath icon = icon("file-missing");
        icon.getStyleClass().add("file-icon-missing");
        return icon;
    }

    private static SVGPath icon(String name) {

        SVGPath icon = Icons.shape(name);
        icon.getStyleClass().add("file-icon");
        return icon;
    }
}
