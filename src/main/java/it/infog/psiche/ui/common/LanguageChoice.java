package it.infog.psiche.ui.common;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A language to choose from a list, by name in the author's own language, which the author can also search. It shows
 * the language chosen; clicking it lists them all, and typing narrows the list to the languages whose name or tag
 * contains what was typed, ignoring case and accents, so "bokmal" finds "Norwegian Bokmål" and "it-" every Italian.
 * Names that start with it come first, then those with a word that does, so "ital" lists Italian before Catalan
 * (Italy). The arrow keys move through the list, Enter or a click picks a language, and Escape, or leaving the field,
 * keeps the one chosen before. Each is a language alone or with a country, such as "Italian" or "Italian (Italy)".
 */
public final class LanguageChoice extends StackPane {

    private static final int VISIBLE_ROWS = 12;
    private static final double ROW_HEIGHT = 27;
    private static final int NO_MATCH = Integer.MAX_VALUE;

    private final List<Locale> languages;
    private final TextField field = new TextField();
    private final ListView<Locale> list = new ListView<>();
    private final Popup popup = new Popup();
    private final ReadOnlyObjectWrapper<Locale> value = new ReadOnlyObjectWrapper<>();

    /** @param tag the language chosen at first, as a BCP 47 tag such as {@code en-US} */
    public LanguageChoice(String tag) {

        value.set(Locale.forLanguageTag(tag));
        languages = new ArrayList<>(Locale.availableLocales()
                .filter(locale -> !locale.getLanguage().isEmpty())
                .filter(locale -> locale.getScript().isEmpty() && locale.getVariant().isEmpty())
                .filter(locale -> !locale.hasExtensions())
                .map(Locale::stripExtensions)
                .distinct()
                .sorted(Comparator.comparing(locale -> locale.getDisplayName().toLowerCase(Locale.ROOT)))
                .toList());
        if (!languages.contains(value.get()))
            languages.addFirst(value.get());

        //the list, under the field; it never takes the focus, so typing goes on in the field
        list.setFocusTraversable(false);
        list.setPlaceholder(new Label("No language matches"));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Locale locale, boolean empty) {
                super.updateItem(locale, empty);
                setText(empty || locale == null ? null : locale.getDisplayName());
            }
        });
        list.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && list.getSelectionModel().getSelectedItem() != null)
                pick(list.getSelectionModel().getSelectedItem());
        });

        //frame
        StackPane frame = new StackPane(list);
        frame.getStyleClass().add("combo-box-popup");

        //popup
        popup.getContent().add(frame);
        popup.setAutoHide(true);
        // A click elsewhere closes the list and gives up the search.
        popup.setOnAutoHide(event -> showValue());

        //the field
        field.setPromptText("Type to search");
        // The chosen language's name is no search.
        field.textProperty().addListener((obs, old, text) -> {
            if (!text.equals(value.get().getDisplayName()))
                narrow(text);
        });
        field.setOnMouseClicked(event -> {
            if (popup.isShowing())
                popup.hide();
            else
                open(languages);
        });
        field.addEventFilter(KeyEvent.KEY_PRESSED, this::navigate);
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (focused)
                Platform.runLater(field::selectAll);
            else {
                popup.hide();
                showValue();
            }
        });

        // Drawn like a combo box's arrow, so the field reads as a list to choose from.
        Region arrow = new Region();
        arrow.setShape(Icons.shape("list-arrow"));
        arrow.getStyleClass().add("arrow");
        arrow.setMouseTransparent(true);
        StackPane.setAlignment(arrow, Pos.CENTER_RIGHT);
        StackPane.setMargin(arrow, new Insets(0, 12, 0, 0));

        getChildren().addAll(field, arrow);
        getStyleClass().add("language-choice");
        setMaxWidth(Double.MAX_VALUE);
        showValue();
    }

    /** The language chosen. */
    public Locale getValue() {

        return value.get();
    }

    /** The language chosen, which changes as the author picks another. */
    public ReadOnlyObjectProperty<Locale> valueProperty() {

        return value.getReadOnlyProperty();
    }

    /** Chooses {@code language}, as if the author had picked it, and closes the list if it is open. */
    public void setValue(Locale language) {

        if (!languages.contains(language))
            languages.addFirst(language);
        value.set(language);
        popup.hide();
        showValue();
    }

    /** Lists the languages that match {@code text}, or all of them for none. */
    private void narrow(String text) {

        // An empty search ranks every language first. Sorting is stable, so each rank keeps the alphabetical order.
        String search = searchable(text);
        open(languages.stream()
                .filter(locale -> rank(locale, search) < NO_MATCH)
                .sorted(Comparator.comparingInt(locale -> rank(locale, search)))
                .toList());
    }

    /**
     * Shows {@code matching} under the field, the chosen language selected if among them, else the first; with none,
     * the list says so.
     */
    private void open(List<Locale> matching) {

        list.getItems().setAll(matching);
        list.setPrefSize(field.getWidth(), Math.clamp(matching.size(), 1, VISIBLE_ROWS) * ROW_HEIGHT + 2);
        if (!matching.isEmpty()) {
            int chosen = Math.max(matching.indexOf(value.get()), 0);
            list.getSelectionModel().select(chosen);
            list.scrollTo(Math.max(chosen - VISIBLE_ROWS / 2, 0));
        }

        // The popup takes its stylesheets from the main window as it opens.
        if (!popup.isShowing() && getScene() != null) {
            Bounds bounds = field.localToScreen(field.getBoundsInLocal());
            popup.show(this, bounds.getMinX(), bounds.getMaxY() + 2);
        }
    }

    /** Up and Down move through the list, Enter picks, Escape closes it; with the list closed, keys work as usual. */
    private void navigate(KeyEvent event) {

        switch (event.getCode()) {
            case DOWN, UP -> {
                if (!popup.isShowing())
                    open(languages);
                else {
                    if (event.getCode() == KeyCode.DOWN)
                        list.getSelectionModel().selectNext();
                    else
                        list.getSelectionModel().selectPrevious();
                    list.scrollTo(Math.max(list.getSelectionModel().getSelectedIndex() - VISIBLE_ROWS / 2, 0));
                }
                event.consume();
            }
            case ENTER -> {
                if (popup.isShowing()) {
                    pick(list.getSelectionModel().getSelectedItem());
                    event.consume();
                }
            }
            case ESCAPE -> {
                if (popup.isShowing()) {
                    popup.hide();
                    showValue();
                    event.consume();
                }
            }
            default -> {
            }
        }
    }

    private void pick(Locale language) {

        if (language != null)
            value.set(language);
        popup.hide();
        showValue();

        // Selected, so typing again starts a new search instead of adding to the name.
        field.selectAll();
    }

    /** Puts the chosen language's name in the field. */
    private void showValue() {

        field.setText(value.get().getDisplayName());
    }

    /**
     * How well {@code locale} matches {@code search}: 0 if its name starts with it, 1 if a word of its name does, 2 if
     * its tag does, 3 if its name contains it anywhere, or {@link #NO_MATCH}.
     */
    private static int rank(Locale locale, String search) {

        String name = searchable(locale.getDisplayName());
        if (name.startsWith(search))
            return 0;
        if (name.contains(" " + search) || name.contains("(" + search))
            return 1;
        if (locale.toLanguageTag().toLowerCase(Locale.ROOT).startsWith(search))
            return 2;
        if (name.contains(search))
            return 3;

        return NO_MATCH;
    }

    /** {@code text} in lower case without accents, so "Bokmål" and "bokmal" compare equal. */
    private static String searchable(String text) {

        return Normalizer.normalize(text.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
