package it.infog.psiche.ui.preferences;

import it.infog.psiche.ui.common.AppInfo;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.update.LatestRelease;
import it.infog.psiche.update.model.Release;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * The Preferences dialog's Check for updates button: asks psiche.app for the latest release and, when it is newer
 * than the app, offers to open the site's download page in the browser. The site is asked off the FX thread.
 */
final class UpdateCheck {

    static final String CHECK = "Check for updates";
    private static final String CHECKING = "Checking";
    private static final ButtonType DOWNLOAD = new ButtonType("Download", ButtonData.OK_DONE);
    private static final ButtonType NOT_NOW = new ButtonType("Not now", ButtonData.CANCEL_CLOSE);

    private UpdateCheck() {
    }

    /**
     * Makes {@code button}, one of a dialog's, check for updates instead of closing the dialog.
     *
     * @param browser opens a web address in the system's default browser
     */
    static void attach(Button button, Consumer<String> browser) {

        // A run straight from the sources has no version to compare.
        button.setDisable(AppInfo.rawVersion().isEmpty());
        button.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            check(button, browser);
        });
    }

    private static void check(Button button, Consumer<String> browser) {

        String installed = AppInfo.rawVersion().orElseThrow();
        // Keeps the width it has with its own text, so the bar doesn't shift while it checks.
        button.setMinWidth(button.getWidth());
        button.setPrefWidth(button.getWidth());
        button.setDisable(true);
        button.setText(CHECKING);

        LatestRelease.fetch(installed).whenComplete((release, error) -> Platform.runLater(() -> {
            button.setDisable(false);
            button.setText(CHECK);
            // Preferences was closed meanwhile: nothing to show the answer above.
            Window owner = button.getScene() == null ? null : button.getScene().getWindow();
            if (owner == null || !owner.isShowing())
                return;

            if (error != null)
                Dialogs.showError(owner, "Could not check for updates",
                        error instanceof CompletionException ? error.getCause() : error);
            else
                answer(owner, release, installed, browser);
        }));
    }

    private static void answer(Window owner, Release release, String installed, Consumer<String> browser) {

        String current = AppInfo.version().orElse(installed);
        if (!LatestRelease.isNewer(release, installed)) {
            Dialogs.inform(owner, AppInfo.NAME + " is up to date",
                    "Version " + current + " is the latest release.");
            return;
        }

        String question = "You have version %s. Download the new one from %s?"
                .formatted(current, AppInfo.WEBSITE_SHOWN);
        Dialogs.choose(owner, AppInfo.NAME + " " + release.version() + " is out", question, DOWNLOAD, NOT_NOW)
                .filter(DOWNLOAD::equals)
                .ifPresent(choice -> browser.accept(release.downloadUrl()));
    }
}
