package it.infog.psiche.ui.media;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.markdown.ImageLines;
import it.infog.psiche.markdown.model.ImageLine;
import it.infog.psiche.media.ChapterMedia;
import it.infog.psiche.media.ImageFiles;
import it.infog.psiche.media.ImageUses;
import it.infog.psiche.media.model.ChapterImage;
import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.ui.chapters.ChapterEditor;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.ImageFilter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.common.WorkThread;

import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Insert image: while the chapter's images include some its text does not show, the author is offered those first, in
 * the {@link UnshownImagesDialog}, or another file; otherwise the author picks a JPEG or PNG file straight away. The
 * image shows in the {@link InsertImageDialog} with a caption to write and a warning if it will look poor in the
 * exports; it is copied into the chapter's folder in {@code media/}, unless it is there already, and written into the
 * chapter after the caret's line, as one change Undo reverts. Modify image shows an image of the chapter in the same
 * dialog, to change its caption or, with Choose another, its file. Files are read and copied on a thread of their
 * own; the first image of a book also keeps the chapters' images out of Git.
 */
public final class ImageInsertion {

    private final ChapterEditor editor;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final Runnable keepOutOfGit;
    private final WorkThread thread = new WorkThread("Images");
    private Runnable onCopied = () -> {};

    // Where the file chooser opens: the user's home, then the folder of the image last chosen.
    private Path lastFolder = Path.of(System.getProperty("user.home"));

    /** @param keepOutOfGit keeps the chapters' images out of the book's Git, once an image is copied into it */
    public ImageInsertion(ChapterEditor editor, StatusBar statusBar, ErrorReporter errors, Supplier<Window> window,
                          Runnable keepOutOfGit) {

        this.editor = editor;
        this.statusBar = statusBar;
        this.errors = errors;
        this.window = window;
        this.keepOutOfGit = keepOutOfGit;
    }

    /** Asks for an image to insert into the open chapter; nothing with no chapter open, or while one is on its way. */
    public void insert() {

        Optional<BookFolder> book = editor.book();
        Optional<Chapter> chapter = editor.chapter();
        if (book.isPresent() && chapter.isPresent() && !thread.isBusy())
            offer(book.get(), chapter.get());
    }

    /**
     * Modify image: shows {@code line}'s image, read off the FX thread, with its caption to change; a missing file
     * shows no preview. Choose another swaps the file, copied into the chapter's folder as Insert does, and keeps the
     * caption; the old file stays in the folder. The line is written anew in place, as one change Undo reverts.
     */
    public void modify(ImageLine line) {

        Optional<BookFolder> book = editor.book();
        Optional<Chapter> chapter = editor.chapter();
        if (book.isEmpty() || chapter.isEmpty() || thread.isBusy())
            return;

        Optional<Path> file = ImageLines.file(book.get().chaptersDir(), line.destination());
        String fileName = file.map(path -> path.getFileName().toString()).orElse(line.destination());
        thread.run(() -> file.flatMap(ImageFiles::tryRead),
                image -> askModify(book.get(), chapter.get(), line, new Shown(image, fileName, false), line.caption()),
                failure -> errors.report("Cannot read " + fileName, failure));
    }

    /** Runs {@code action} each time an image is copied into a chapter's folder. */
    public void setOnCopied(Runnable action) {

        onCopied = action;
    }

    /**
     * Replace image: asks for a better file for {@code image}, copies it into the chapter's folder, and makes every
     * paragraph that shows {@code image} show it instead, with its caption. The old file stays in the folder, no longer
     * in the chapter, for the author to keep or detach.
     */
    public void replace(MediaImage image) {

        Optional<BookFolder> book = editor.book();
        Optional<Chapter> chapter = editor.chapter();
        if (book.isEmpty() || chapter.isEmpty() || thread.isBusy())
            return;

        Optional<Path> file = chooseFile();
        if (file.isEmpty())
            return;

        lastFolder = file.get().getParent();
        Path chaptersDir = book.get().chaptersDir();
        String title = chapter.get().title();
        ChapterMedia media = new ChapterMedia(book.get().mediaDir());
        thread.run(() -> media.add(title, ImageFiles.read(file.get()).file()), copied -> {
            onCopied.run();
            String destination = ImageLines.destination(title, copied.getFileName().toString());
            if (editor.edits().change(chapter.get(),
                    text -> ImageUses.withDestination(text, chaptersDir, image.file(), destination)))
                statusBar.info("Replaced %s with %s; %s is no longer in the chapter".formatted(image.name(),
                        copied.getFileName(), image.name()));
        }, failure -> refuse(file.get(), failure));
    }

    /** Stops the thread, interrupting a copy that runs. */
    public void shutdown() {

        thread.shutdown();
    }

    /**
     * Offers the chapter's images its text does not show, read off the FX thread, before another file; with none,
     * asks for a file straight away.
     */
    private void offer(BookFolder book, Chapter chapter) {

        String text = editor.text();
        Path chaptersDir = book.chaptersDir();
        ChapterMedia media = new ChapterMedia(book.mediaDir());
        thread.run(() -> ImageUses.of(media.images(chapter.title()), ImageLines.of(text), chaptersDir).stream()
                .filter(use -> !use.inChapter())
                .map(ChapterImage::image)
                .toList(), unshown -> {
            // The author may have opened another chapter while the folder was read.
            if (!editor.isOpen(chapter))
                return;
            if (unshown.isEmpty()) {
                choose(book, chapter);
                return;
            }

            UnshownImagesDialog dialog = new UnshownImagesDialog(window.get(), unshown);
            Optional<MediaImage> picked = dialog.showAndWait();
            if (picked.isPresent())
                ask(book, chapter, picked.get(), true);
            else if (dialog.wantsFile())
                choose(book, chapter);
        }, failure -> errors.report("Cannot read the images of " + chapter.title(), failure));
    }

    /** Asks for the image's file, then reads it off the FX thread and shows it. */
    private void choose(BookFolder book, Chapter chapter) {

        Optional<Path> file = chooseFile();
        if (file.isEmpty())
            return;

        lastFolder = file.get().getParent();
        thread.run(() -> ImageFiles.read(file.get()), image -> ask(book, chapter, image, false),
                failure -> refuse(file.get(), failure));
    }

    private Optional<Path> chooseFile() {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Insert image");
        chooser.getExtensionFilters().add(ImageFilter.jpegOrPng());
        if (Files.isDirectory(lastFolder))
            chooser.setInitialDirectory(lastFolder.toFile());

        return Optional.ofNullable(chooser.showOpenDialog(window.get())).map(File::toPath);
    }

    /**
     * Shows {@code image} to the author, who inserts it with a caption, chooses another, or cancels.
     *
     * @param inFolder true for one of the chapter's images already: it is not copied, and choosing another offers the
     *                 chapter's images again
     */
    private void ask(BookFolder book, Chapter chapter, MediaImage image, boolean inFolder) {

        // The author may have opened another chapter while the file was read.
        if (!editor.isOpen(chapter))
            return;

        InsertImageDialog dialog = InsertImageDialog.inserting(window.get(), image, chapter.title(), inFolder);
        ButtonType choice = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == InsertImageDialog.CHOOSE_ANOTHER) {
            if (inFolder)
                offer(book, chapter);
            else
                choose(book, chapter);
        } else if (choice == InsertImageDialog.INSERT) {
            String caption = dialog.caption();
            if (inFolder)
                write(chapter, image.name(), caption);
            else
                copy(book, chapter, image.file(), name -> write(chapter, name, caption));
        }
    }

    /**
     * Shows {@code shown} as {@code line}'s image, with {@code caption}, to the author, who modifies it, chooses
     * another file, or cancels.
     */
    private void askModify(BookFolder book, Chapter chapter, ImageLine line, Shown shown, String caption) {

        // The author may have opened another chapter while the file was read.
        if (!editor.isOpen(chapter))
            return;

        InsertImageDialog dialog = InsertImageDialog.modifying(window.get(), shown.image(), shown.fileName(), caption,
                chapter.title(), shown.chosen());
        ButtonType choice = dialog.showAndWait().orElse(ButtonType.CANCEL);
        String modified = dialog.caption();
        if (choice == InsertImageDialog.CHOOSE_ANOTHER)
            chooseFor(book, chapter, line, modified);
        else if (choice == InsertImageDialog.MODIFY && shown.chosen())
            copy(book, chapter, shown.image().orElseThrow().file(), name -> rewrite(chapter, line,
                    ImageLines.markdown(modified, ImageLines.destination(chapter.title(), name))));
        else if (choice == InsertImageDialog.MODIFY)
            rewrite(chapter, line, ImageLines.markdown(modified, line.destination()));
    }

    /** Asks for a file in place of {@code line}'s image, reads it off the FX thread, and shows it with the caption. */
    private void chooseFor(BookFolder book, Chapter chapter, ImageLine line, String caption) {

        Optional<Path> file = chooseFile();
        if (file.isEmpty())
            return;

        lastFolder = file.get().getParent();
        thread.run(() -> ImageFiles.read(file.get()),
                image -> askModify(book, chapter, line, new Shown(Optional.of(image), image.name(), true), caption),
                failure -> refuse(file.get(), failure));
    }

    /**
     * Copies {@code file} into the chapter's folder off the FX thread, then writes it into the chapter with
     * {@code place}, given the copy's name, which returns false if the chapter is no longer open.
     */
    private void copy(BookFolder book, Chapter chapter, Path file, Predicate<String> place) {

        ChapterMedia media = new ChapterMedia(book.mediaDir());
        thread.run(() -> media.add(chapter.title(), file), copied -> {
            keepOutOfGit.run();
            onCopied.run();
            String name = copied.getFileName().toString();
            if (!place.test(name))
                statusBar.info("Copied %s to media/%s/; open the chapter to insert it from the Media tab".formatted(
                        name, chapter.title()));
        }, failure -> errors.report("Cannot copy " + file.getFileName() + " into the book", failure));
    }

    /**
     * Writes {@code line} anew as {@code markdown}, in place, if it is still in the open {@code chapter}.
     *
     * @return false if the chapter is no longer open
     */
    private boolean rewrite(Chapter chapter, ImageLine line, String markdown) {

        if (!editor.edits().change(chapter, text -> ImageLines.withLine(text, line, markdown)))
            return false;

        statusBar.info("Modified the image");
        return true;
    }

    /**
     * Writes the image {@code name} of the chapter's folder into {@code chapter} after the caret's line, with
     * {@code caption}.
     *
     * @return false if the chapter is no longer open
     */
    private boolean write(Chapter chapter, String name, String caption) {

        String line = ImageLines.markdown(caption, ImageLines.destination(chapter.title(), name));
        if (!editor.edits().insertImage(chapter, line))
            return false;

        statusBar.info("Inserted " + name);
        return true;
    }

    /** Tells the author why the file cannot be inserted: not a JPEG or PNG image, or not readable at all. */
    private void refuse(Path file, Throwable failure) {

        if (failure instanceof IllegalArgumentException)
            Dialogs.inform(window.get(), "Cannot insert " + file.getFileName(),
                    "It is not a JPEG or PNG image. Save it as one in an image program first.");
        else
            errors.report("Cannot read " + file.getFileName(), failure);
    }

    /**
     * An image as Modify image shows it.
     *
     * @param image    its file as read; empty if it is missing
     * @param fileName its file's name, which a missing one has too
     * @param chosen   true for a file the author chose in place of the image's, not yet copied into the book
     */
    private record Shown(Optional<MediaImage> image,
                         String fileName,
                         boolean chosen) {
    }
}
