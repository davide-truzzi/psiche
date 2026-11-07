package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.TextLinks;
import it.infog.psiche.markdown.model.HeadingAnchor;
import it.infog.psiche.markdown.model.LinkTarget;
import it.infog.psiche.markdown.model.enums.LinkKind;
import it.infog.psiche.ui.common.Controls;
import it.infog.psiche.ui.common.Dialogs;

import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;

import static it.infog.psiche.ui.common.Controls.setShown;

/**
 * Asks for a link's text and where it leads: a web address, or a chapter of the book and, if the author picks one, a
 * heading in it. Modify link… opens it on the link as it is; a chapter or heading the link names that is not there
 * any more shows as not found, so the author sees what was lost and picks another.
 */
final class LinkDialog {

    private static final double LABEL_WIDTH = 80;
    private static final String NOT_FOUND = "\"%s\" (not found)";
    // The section that is no heading: the chapter's start.
    private static final HeadingAnchor START = new HeadingAnchor(1, "", "");
    // A heading's level that marks one the link names but the chapter does not have.
    private static final int MISSING_LEVEL = 0;
    // How far each level of heading under the chapter's sections is set in, in the section list.
    private static final String INDENT = "   ";

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final ButtonType confirm;
    private final String chapter;
    private final SequencedMap<String, List<HeadingAnchor>> chapters;
    private final TextField text = new TextField();
    private final ComboBox<LinkKind> kind =
            Controls.choices(List.of(LinkKind.WEB, LinkKind.CHAPTER), LinkDialog::kindLabel);
    private final TextField address = new TextField();
    private final ComboBox<String> chapterChoice;
    private final ComboBox<HeadingAnchor> section = Controls.choices(List.of(), LinkDialog::sectionLabel);

    /**
     * @param chapter     the name of the chapter the link is in
     * @param chapters    each chapter of the book by name, in order, with its headings
     * @param text        the link's text to start with: the selection, or the link's own
     * @param destination the destination of the link being modified; empty for a new link
     */
    LinkDialog(Window owner, String chapter, SequencedMap<String, List<HeadingAnchor>> chapters, String text,
               Optional<String> destination) {

        this.chapter = chapter;
        this.chapters = chapters;
        chapterChoice = Controls.choices(List.copyOf(chapters.keySet()), this::chapterLabel);
        confirm = new ButtonType(destination.isPresent() ? "Save" : "Insert", ButtonData.OK_DONE);

        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText(destination.isPresent() ? "Modify link" : "Insert link");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, confirm);
        dialog.getDialogPane().lookupButton(confirm).getStyleClass().add("primary");
        dialog.getDialogPane().setContent(content());

        this.text.setText(text.strip());
        destination.ifPresentOrElse(this::show, () -> kind.setValue(LinkKind.WEB));
        dialog.getDialogPane().lookupButton(confirm).disableProperty().bind(Bindings.createBooleanBinding(
                () -> !isComplete(), this.text.textProperty(), kind.valueProperty(), address.textProperty(),
                chapterChoice.valueProperty(), section.valueProperty()));
        dialog.setOnShown(event -> this.text.requestFocus());
    }

    /** Shows the dialog until the author confirms or cancels it; true if they confirmed. */
    boolean showAndWait() {

        return dialog.showAndWait().filter(confirm::equals).isPresent();
    }

    /** The link's text as the author left it. */
    String text() {

        return text.getText().strip();
    }

    /** Where the link leads, as the chapter it is in writes it. */
    String destination() {

        return switch (kind.getValue()) {
            case CHAPTER ->
                    TextLinks.chapterDestination(chapter, chapterChoice.getValue(), section.getValue().anchor());
            case WEB, MALFORMED -> TextLinks.webAddress(address.getText());
        };
    }

    private Node content() {

        //fields
        text.setPromptText("The words the reader sees");
        address.setPromptText("https://…");
        chapterChoice.setPromptText("Choose a chapter");
        chapterChoice.valueProperty().addListener((obs, old, chosen) -> showSections(chosen, ""));
        Label wrongAddress = Controls.hint("Not a web or mail address.");
        wrongAddress.getStyleClass().add("wrong-address");
        wrongAddress.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !address.getText().isBlank() && !isWebAddress(), address.textProperty()));

        //groups, the web's or the chapter's shown as the link leads
        GridPane common = form();
        common.addRow(0, new Label("Text"), text);
        common.addRow(1, new Label("Link to"), kind);

        GridPane web = form();
        web.addRow(0, new Label("Address"), address);
        web.add(wrongAddress, 1, 1);

        GridPane book = form();
        book.addRow(0, new Label("Chapter"), chapterChoice);
        book.addRow(1, new Label("Section"), section);

        kind.valueProperty().addListener((obs, old, chosen) -> {
            setShown(web, chosen == LinkKind.WEB);
            setShown(book, chosen == LinkKind.CHAPTER);
            if (dialog.isShowing())
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
        });
        setShown(book, false);

        VBox content = new VBox(common, web, book);
        content.getStyleClass().add("link-dialog-content");
        return content;
    }

    /** Opens the dialog on {@code destination}, the link's as it is. */
    private void show(String destination) {

        LinkTarget found = TextLinks.target(destination, chapter);
        if (found.kind() != LinkKind.CHAPTER) {
            kind.setValue(LinkKind.WEB);
            address.setText(destination);
            return;
        }

        kind.setValue(LinkKind.CHAPTER);
        if (!chapters.containsKey(found.chapter()))
            chapterChoice.getItems().add(found.chapter());
        chapterChoice.setValue(found.chapter());
        showSections(found.chapter(), found.anchor());
    }

    /** Lists the sections of {@code name}, the chapter chosen, and picks the one at {@code anchor}, or its start. */
    private void showSections(String name, String anchor) {

        List<HeadingAnchor> headings = name == null ? List.of() : chapters.getOrDefault(name, List.of());
        section.getItems().setAll(START);
        // The first heading, at level 1, is the chapter's title, which a link to the chapter leads to already.
        headings.stream().skip(!headings.isEmpty() && headings.getFirst().level() == 1 ? 1 : 0)
                .forEach(section.getItems()::add);

        if (section.getItems().stream().noneMatch(heading -> heading.anchor().equals(anchor)))
            section.getItems().add(new HeadingAnchor(MISSING_LEVEL, anchor, anchor));
        section.getItems().stream().filter(heading -> heading.anchor().equals(anchor)).findFirst()
                .ifPresent(section::setValue);
    }

    /** True once the link has text and leads somewhere that is there. */
    private boolean isComplete() {

        if (text.getText().isBlank() || kind.getValue() == null)
            return false;

        return switch (kind.getValue()) {
            case CHAPTER -> chapters.containsKey(chapterChoice.getValue()) && section.getValue() != null
                    && section.getValue().level() != MISSING_LEVEL;
            case WEB, MALFORMED -> isWebAddress();
        };
    }

    private boolean isWebAddress() {

        return TextLinks.target(TextLinks.webAddress(address.getText()), chapter).kind() == LinkKind.WEB;
    }

    /** A form of labels and fields, its labels' column as wide in every group, so the fields line up. */
    private static GridPane form() {

        GridPane form = new GridPane();
        form.getStyleClass().add("book-form");
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(LABEL_WIDTH);
        form.getColumnConstraints().addAll(labels, new ColumnConstraints());
        return form;
    }

    private String chapterLabel(String name) {

        return chapters.containsKey(name) ? name : NOT_FOUND.formatted(name);
    }

    private static String kindLabel(LinkKind kind) {

        return kind == LinkKind.CHAPTER ? "A chapter of the book" : "A web address";
    }

    private static String sectionLabel(HeadingAnchor heading) {

        if (heading.anchor().isEmpty())
            return "Start of the chapter";
        if (heading.level() == MISSING_LEVEL)
            return NOT_FOUND.formatted(heading.anchor());

        return INDENT.repeat(Math.max(0, heading.level() - 2)) + heading.text();
    }
}
