package it.infog.psiche.ui.export;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.BookAssembler;
import it.infog.psiche.export.BookExporter;
import it.infog.psiche.export.ChapterExporter;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ChapterText;
import it.infog.psiche.export.model.enums.EmptyChapters;
import it.infog.psiche.export.model.enums.EpubVariant;
import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;

import javafx.concurrent.Task;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Exports a chapter or the whole book to a file the author picks, by default next to the book's folder rather than
 * inside it, where it would be one more change to commit. Unsaved changes are settled first, so the export holds what
 * is saved, and the file is written on a thread of its own, so the window stays responsive while a PDF is laid out.
 */
public final class Exports {

    /** The formats the whole book can be exported to. */
    public static final List<ExportFormat> BOOK_FORMATS =
            List.of(ExportFormat.PDF, ExportFormat.ODT, ExportFormat.EPUB, ExportFormat.EPUB_KINDLE);

    /** The formats a chapter can be exported to. */
    public static final List<ExportFormat> CHAPTER_FORMATS =
            List.of(ExportFormat.PDF, ExportFormat.ODT, ExportFormat.DOCX, ExportFormat.MARKDOWN);

    private static final ButtonType PLACEHOLDERS = new ButtonType("Export as placeholders", ButtonData.YES);
    private static final ButtonType SKIP = new ButtonType("Skip them", ButtonData.NO);
    private static final ButtonType EXPORT_ANYWAY = new ButtonType("Export anyway", ButtonData.YES);

    // One thread, so exports finish in the order the author asked for them, off the FX thread.
    private final ExecutorService thread =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("export").daemon().factory());
    private final ChapterExporter chapterExporter = new ChapterExporter();
    private final BookExporter bookExporter = new BookExporter();
    private final Predicate<Chapter> unsaved;
    private final Predicate<String> settleUnsaved;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Supplier<Window> window;

    /**
     * @param unsaved       tells whether a chapter has unsaved changes
     * @param settleUnsaved asks the author, with the question given, to save or discard every unsaved change;
     *                      returning false cancels the export
     */
    public Exports(Predicate<Chapter> unsaved, Predicate<String> settleUnsaved, StatusBar statusBar,
                   ErrorReporter errors, Supplier<Window> window) {

        this.unsaved = unsaved;
        this.settleUnsaved = settleUnsaved;
        this.statusBar = statusBar;
        this.errors = errors;
        this.window = window;
    }

    /** Writes {@code chapter} of {@code book} as it is saved, asking first about its unsaved changes, if any. */
    public void exportChapter(BookFolder book, Chapter chapter, ExportFormat format) {

        if (unsaved.test(chapter) && !settleUnsaved.test("Save them before exporting?"))
            return;

        Optional<Path> target = Dialogs.chooseSaveFile(window.get(), "Export " + chapter.title(),
                book.root().getParent(), chapter.title(), format.label(), format.extension());
        if (target.isEmpty())
            return;

        String markdown;
        BookProperties properties;
        try {
            markdown = book.read(chapter);
            properties = book.properties();
        } catch (UncheckedIOException e) {
            errors.report("Cannot export " + chapter.title(), e);
            return;
        }

        Path chaptersDir = book.chaptersDir();
        run(chapter.title(), target.get(), () -> {
            switch (format) {
                case PDF -> chapterExporter.manuscriptPdf(markdown, chapter.title(), properties, chaptersDir,
                        target.get());
                case ODT -> chapterExporter.manuscriptOdt(markdown, chapter.title(), properties, chaptersDir,
                        target.get());
                case DOCX -> chapterExporter.manuscriptDocx(markdown, chapter.title(), properties, chaptersDir,
                        target.get());
                case MARKDOWN -> chapterExporter.markdown(markdown, target.get());
            }
        });
    }

    /**
     * Writes the whole of {@code book} as it is saved: every chapter, in the order of the chapter list, each starting
     * on a new page, after a title page. Unsaved changes in any chapter are settled first, and if some chapters are
     * empty the author chooses whether to skip them or keep their place with their file name. For an EPUB, what
     * stores would object to, such as a small cover, is listed for the author to fix or accept.
     *
     * @param format one of {@link #BOOK_FORMATS}
     */
    public void exportBook(BookFolder book, ExportFormat format) {

        if (!settleUnsaved.test("Save them before exporting the book?"))
            return;

        //read the book
        BookProperties properties;
        List<ChapterText> chapters;
        try {
            properties = book.properties();
            chapters = book.chapters().stream()
                    .map(chapter -> new ChapterText(chapter.title(), book.read(chapter)))
                    .toList();
        } catch (UncheckedIOException e) {
            errors.report("Cannot export the book", e);
            return;
        }

        if (chapters.isEmpty()) {
            Dialogs.inform(window.get(), "Nothing to export", "The book has no chapters yet.");
            return;
        }

        Optional<EmptyChapters> empty = askAboutEmptyChapters(chapters);
        if (empty.isEmpty())
            return;

        BookExport assembled = BookAssembler.assemble(properties, book.cover(), book.stylesheet(), book.chaptersDir(),
                chapters, empty.get());
        if (assembled.chapters().isEmpty()) {
            Dialogs.inform(window.get(), "Nothing to export", "Every chapter of the book is empty.");
            return;
        }

        Optional<EpubVariant> epub = switch (format) {
            case EPUB -> Optional.of(EpubVariant.STORES);
            case EPUB_KINDLE -> Optional.of(EpubVariant.KINDLE);
            default -> Optional.empty();
        };
        if (epub.isPresent() && !acceptProblems(bookExporter.epubProblems(assembled, epub.get())))
            return;

        //where
        String name = Dialogs.fileName(properties.title()) + (format == ExportFormat.EPUB_KINDLE ? " (Kindle)" : "");
        Optional<Path> target = Dialogs.chooseSaveFile(window.get(), "Export " + properties.title(),
                book.root().getParent(), name, format.label(), format.extension());
        if (target.isEmpty())
            return;

        //write
        run(properties.title(), target.get(), () -> {
            switch (format) {
                case PDF -> bookExporter.manuscriptPdf(assembled, target.get());
                case ODT -> bookExporter.manuscriptOdt(assembled, target.get());
                case EPUB, EPUB_KINDLE -> bookExporter.epub(assembled, epub.get(), target.get());
                case MARKDOWN -> throw new IllegalArgumentException("The book cannot be exported to Markdown");
            }
        });
    }

    /** Stops the export thread, interrupting a running export. */
    public void shutdown() {

        thread.shutdownNow();
    }

    /**
     * Lists the chapters with no text and asks whether to leave them out or keep each as a page showing its file name,
     * to see where it goes in an unfinished book.
     *
     * @return {@link EmptyChapters#SKIP} if no chapter is empty; empty if the author cancels
     */
    private Optional<EmptyChapters> askAboutEmptyChapters(List<ChapterText> chapters) {

        List<String> empty = chapters.stream().filter(ChapterText::isEmpty).map(ChapterText::fileTitle).toList();
        if (empty.isEmpty())
            return Optional.of(EmptyChapters.SKIP);

        String header = empty.size() == 1 ? "1 chapter is empty" : "%d chapters are empty".formatted(empty.size());
        String content = String.join("\n", empty)
                + "\n\nExport each as a page showing its file name, to see where it goes, or leave them out?";

        Optional<ButtonType> choice = Dialogs.choose(window.get(), header, content, PLACEHOLDERS, SKIP,
                ButtonType.CANCEL);
        if (choice.filter(PLACEHOLDERS::equals).isPresent())
            return Optional.of(EmptyChapters.PLACEHOLDER);
        if (choice.filter(SKIP::equals).isPresent())
            return Optional.of(EmptyChapters.SKIP);

        return Optional.empty();
    }

    /**
     * Lists what stores would object to in the export, such as a small cover, and asks whether to export anyway.
     *
     * @return true if there is nothing to object to, or the author chose to export anyway
     */
    private boolean acceptProblems(List<String> problems) {

        if (problems.isEmpty())
            return true;

        String header = problems.size() == 1 ? "1 thing stores may object to" : "%d things stores may object to"
                .formatted(problems.size());
        Optional<ButtonType> choice = Dialogs.choose(window.get(), header,
                String.join("\n\n", problems) + "\n\nExport anyway, or cancel to fix them first?", EXPORT_ANYWAY,
                ButtonType.CANCEL);

        return choice.filter(EXPORT_ANYWAY::equals).isPresent();
    }

    /** Runs {@code export} on the export thread, telling the author in the status bar when it starts and ends. */
    private void run(String name, Path target, Runnable export) {

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                export.run();
                return null;
            }
        };

        task.setOnSucceeded(event -> statusBar.info("Exported %s to %s".formatted(name, target)));
        task.setOnFailed(event -> errors.report("Cannot export " + name, task.getException()));
        statusBar.info("Exporting %s…".formatted(name));
        thread.execute(task);
    }
}
