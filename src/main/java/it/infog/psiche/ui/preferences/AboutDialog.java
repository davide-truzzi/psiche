package it.infog.psiche.ui.preferences;

import it.infog.psiche.spelling.Dictionaries;
import it.infog.psiche.ui.common.AppInfo;
import it.infog.psiche.ui.common.Controls;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.Logo;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Psiche Writer's name, version and author, the licence it is distributed under, a way to support it, and the
 * licences of the fonts and libraries that ship with it. The licences in the app open in a window of their own; the
 * others, on their publishers' sites, in the browser.
 */
final class AboutDialog {

    private static final String GPL = "/it/infog/psiche/ui/preferences/LICENSE";
    private static final double LOGO_HEIGHT = 120;
    private static final double NAME_WIDTH = 230;

    /**
     * What ships with the app, and the licence it comes under.
     *
     * @param source where the licence's text is: a web address, or the path of a text inside the app
     */
    private record Credit(String name,
                          String licence,
                          String source) {
    }

    private static final List<Credit> CREDITS = List.of(
            new Credit("Libertinus Serif (font)", "SIL Open Font License 1.1",
                    "/it/infog/psiche/ui/fonts/OFL-LibertinusSerif.txt"),
            new Credit("Courier Prime (font)", "SIL Open Font License 1.1",
                    "/it/infog/psiche/ui/fonts/OFL-CourierPrime.txt"),
            new Credit("OpenHTMLtoPDF", "LGPL 2.1 or later",
                    "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"),
            new Credit("Apache PDFBox, FontBox, XmpBox", "Apache License 2.0",
                    "https://www.apache.org/licenses/LICENSE-2.0"),
            new Credit("pdfbox-graphics2d", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
            new Credit("Apache Commons Codec, Logging", "Apache License 2.0",
                    "https://www.apache.org/licenses/LICENSE-2.0"),
            new Credit("JGit", "Eclipse Distribution License 1.0", "https://www.eclipse.org/org/documents/edl-v10.php"),
            new Credit("JavaEWAH", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
            new Credit("commonmark-java", "BSD 2-Clause",
                    "https://github.com/commonmark/commonmark-java/blob/main/LICENSE.txt"),
            new Credit("SLF4J", "MIT License", "https://www.slf4j.org/license.html"),
            new Credit("TwelveMonkeys ImageIO", "BSD 3-Clause", "https://github.com/haraldk/TwelveMonkeys#license"),
            new Credit("RichTextFX, Flowless", "BSD 2-Clause",
                    "https://github.com/FXMisc/RichTextFX/blob/master/LICENSE"),
            new Credit("ReactFX, UndoFX, WellBehavedFX", "BSD 2-Clause", "https://opensource.org/license/bsd-2-clause"),
            new Credit("Apache Lucene (spell checking)", "Apache License 2.0",
                    "https://www.apache.org/licenses/LICENSE-2.0"),
            new Credit("OpenJFX, Java runtime", "GPL 2 with the Classpath Exception",
                    "https://openjdk.org/legal/gplv2+ce.html"));

    private static final String DICTIONARIES = "Spelling dictionaries";
    private static final String DICTIONARIES_LICENCES = "Several, one each";
    private static final String DICTIONARIES_INTRO = "Psiche checks the spelling with Hunspell dictionaries that "
            + "LibreOffice gathers, each under its own licence. Each sits in the app with the README that names its "
            + "authors and states its licence in full.\n\n";

    private AboutDialog() {
    }

    /** @param browser opens a web address in the system's default browser */
    static void show(Window owner, Consumer<String> browser) {

        Dialog<ButtonType> dialog = new Dialog<>();
        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText("About Psiche Writer");
        dialog.getDialogPane().getStyleClass().add("about");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);

        //the app
        Node logo = Logo.lockup(LOGO_HEIGHT);
        logo.getStyleClass().add("about-logo");
        Label version = new Label("Version " + AppInfo.version().orElse("unknown"));
        Label copyright = new Label("© 2026 Davide Truzzi");
        Label licence = new Label("Free software under the GNU General Public License, version 3 or later");
        Hyperlink gpl = link("Read the licence",
                () -> showText(dialog, "GNU General Public License, version 3", read(GPL)));
        Button coffee = CoffeeButton.create(browser);
        Label support = new Label("Help Davide support this project");

        VBox app = new VBox(logo, version, copyright, licence, gpl, coffee, support);
        app.getStyleClass().add("about-app");

        //what ships with it
        GridPane credits = new GridPane();
        for (int row = 0; row < CREDITS.size(); row++) {
            Credit credit = CREDITS.get(row);
            Runnable open = credit.source().startsWith("https://")
                    ? () -> browser.accept(credit.source())
                    : () -> showText(dialog, credit.name() + " · " + credit.licence(), read(credit.source()));
            credits.addRow(row, new Label(credit.name()), link(credit.licence(), open));
        }
        credits.addRow(CREDITS.size(), new Label(DICTIONARIES),
                link(DICTIONARIES_LICENCES, () -> showText(dialog, DICTIONARIES, dictionaries())));

        VBox content = new VBox(app, Controls.fieldset("Licences", credits, NAME_WIDTH));
        content.getStyleClass().add("about-content");
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private static Hyperlink link(String text, Runnable action) {

        Hyperlink link = new Hyperlink(text);
        link.setOnAction(event -> {
            action.run();
            // Visited links would change color, but these lead to the same place each time.
            link.setVisited(false);
        });
        return link;
    }

    /** Shows the text at {@code resource}, a licence inside the app, under {@code title}, above the About dialog. */
    /** The dictionaries the app brings, each with its language, its source and its licence, as the index lists them. */
    private static String dictionaries() {

        return Dictionaries.bundled().stream()
                .map(language -> "%s: %s. %s.".formatted(language.locale().getDisplayName(Locale.ENGLISH),
                        language.source(), language.licence()))
                .collect(Collectors.joining("\n", DICTIONARIES_INTRO, "\n"));
    }

    private static void showText(Dialog<?> about, String title, String content) {

        Dialog<ButtonType> dialog = new Dialog<>();
        Dialogs.ownAndStyle(dialog, about.getDialogPane().getScene().getWindow());
        dialog.setHeaderText(title);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);

        TextArea text = new TextArea(content);
        text.setEditable(false);
        text.getStyleClass().add("licence-text");
        dialog.getDialogPane().setContent(text);
        dialog.setResizable(true);
        dialog.showAndWait();
    }

    private static String read(String resource) {

        try (InputStream in = AboutDialog.class.getResourceAsStream(resource)) {
            if (in == null)
                throw new IllegalStateException("Missing " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
