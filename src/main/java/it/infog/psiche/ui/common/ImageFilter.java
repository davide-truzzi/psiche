package it.infog.psiche.ui.common;

import it.infog.psiche.media.model.enums.ImageFormat;

import javafx.stage.FileChooser;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** The file choosers' filter for the images a book can hold, as the cover or in a chapter: JPEG or PNG. */
public final class ImageFilter {

    private static final String KIND = "JPEG or PNG images";

    private ImageFilter() {
    }

    /** The filter, with each extension in small and capital letters, as cameras often name their files. */
    public static FileChooser.ExtensionFilter jpegOrPng() {

        List<String> patterns = ImageFormat.allExtensions().stream()
                .flatMap(extension -> Stream.of(extension, extension.toUpperCase(Locale.ROOT)))
                .map(extension -> "*." + extension)
                .toList();

        return new FileChooser.ExtensionFilter(KIND, patterns);
    }
}
