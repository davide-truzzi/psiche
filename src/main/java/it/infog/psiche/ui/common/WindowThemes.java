package it.infog.psiche.ui.common;

import it.infog.psiche.settings.model.enums.WindowTheme;

import javafx.scene.Scene;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Puts a window theme into effect: a theme other than the default is a stylesheet loaded after writer.css, which
 * names some of its colours anew and changes a few rules. Dialogs take the window's stylesheets as they open.
 */
public final class WindowThemes {

    private static final String UI_FOLDER = "/it/infog/psiche/ui/";
    private static final List<String> ALL = Arrays.stream(WindowTheme.values())
            .flatMap(theme -> stylesheet(theme).stream())
            .toList();

    private WindowThemes() {
    }

    /** Shows {@code scene} in {@code theme}, in place of the theme it had. */
    public static void apply(Scene scene, WindowTheme theme) {

        scene.getStylesheets().removeAll(ALL);
        stylesheet(theme).ifPresent(scene.getStylesheets()::add);
    }

    /** The stylesheet that loads after writer.css for {@code theme}; empty for the default, writer.css alone. */
    private static Optional<String> stylesheet(WindowTheme theme) {

        return switch (theme) {
            case DEFAULT -> Optional.empty();
            case HIGH_CONTRAST -> Optional.of(resource("high-contrast.css"));
        };
    }

    private static String resource(String name) {

        return WindowThemes.class.getResource(UI_FOLDER + name).toExternalForm();
    }
}
