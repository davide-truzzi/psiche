package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.markdown.HeadingAnchors;
import it.infog.psiche.markdown.TextLinks;
import it.infog.psiche.markdown.model.HeadingAnchor;
import it.infog.psiche.markdown.model.LinkTarget;
import it.infog.psiche.markdown.model.enums.LinkKind;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The open chapter's links: the book's headings they are checked against, Insert, Modify and Remove link, and opening
 * one, Ctrl-clicked in the manuscript or clicked in the preview: a web address in the system browser, a chapter or a
 * heading of the book in the editor. Used on the FX thread.
 */
final class ChapterLinks {

    private final Manuscript manuscript;
    private final Supplier<Optional<Chapter>> chapter;
    private final BookHeadings headings = new BookHeadings(this::checkAgainst);
    private final LinkActions actions;
    // Each chapter of the book with the anchors of its headings, as saved; null until they are read.
    private Map<String, Set<String>> anchors;
    private Runnable onChecked = () -> {};
    private Supplier<List<Chapter>> chapters = List::of;
    private Consumer<Chapter> show = chapter -> {};
    private Consumer<String> browser = address -> {};

    /** @param chapter the open chapter, if any */
    ChapterLinks(Manuscript manuscript, Supplier<Optional<Chapter>> chapter) {

        this.manuscript = manuscript;
        this.chapter = chapter;
        actions = new LinkActions(manuscript, chapter, headings);
        manuscript.setLinkOpening(this::leadsSomewhere, this::follow);
    }

    /** Insert, Modify and Remove link, for the manuscript's right-click menu. */
    LinkActions actions() {

        return actions;
    }

    /** Takes {@code found}, each chapter's headings in the book's order, as saved, to check the links against. */
    void takeHeadings(SequencedMap<String, List<HeadingAnchor>> found) {

        headings.take(found);
    }

    /** Drops the book's headings, as no book is open. */
    void clear() {

        headings.clear();
    }

    /**
     * Opens the chapters links lead to with {@code show}, one of {@code chapters}, the book's, as the chapter list
     * opens them.
     */
    void setChapters(Supplier<List<Chapter>> chapters, Consumer<Chapter> show) {

        this.chapters = chapters;
        this.show = show;
    }

    /** Opens web and mail addresses with {@code browser}, the system's default application. */
    void setBrowser(Consumer<String> browser) {

        this.browser = browser;
    }

    /** Runs {@code action} each time the book's headings are read again, against which the links are checked anew. */
    void setOnChecked(Runnable action) {

        onChecked = action;
    }

    /**
     * What tells, for the open chapter as its text is now, the destination of a link that leads nowhere; none does
     * while no chapter is open, or before the book's headings are read.
     */
    Predicate<String> brokenCheck() {

        Optional<Chapter> open = chapter.get();
        if (open.isEmpty() || anchors == null)
            return destination -> false;

        String name = open.get().title();
        Set<String> own = HeadingAnchors.anchors(manuscript.text());
        Map<String, Set<String>> book = anchors;
        return destination -> TextLinks.isBroken(destination, name, own, book);
    }

    /**
     * Follows a link the author clicked, in the manuscript or in the preview, the one place that tells where a link
     * leads: a web or mail address opens in the system browser, as from the welcome page with no chapter open; a
     * chapter link of the open chapter's opens that chapter, unless it is open, at the heading it names or at its
     * start. Nothing for a link that leads nowhere.
     */
    void follow(String destination) {

        if (!leadsSomewhere(destination))
            return;

        Optional<Chapter> open = chapter.get();
        LinkTarget target = TextLinks.target(destination, open.map(Chapter::title).orElse(""));
        if (target.kind() == LinkKind.WEB)
            browser.accept(destination);
        else
            openChapter(open.orElseThrow(), target);
    }

    /**
     * True if {@code destination} leads somewhere: a web or mail address always, as the welcome's with no chapter open;
     * a link of the open chapter's to a chapter or heading that is there.
     */
    private boolean leadsSomewhere(String destination) {

        Optional<Chapter> open = chapter.get();
        LinkTarget target = TextLinks.target(destination, open.map(Chapter::title).orElse(""));
        return switch (target.kind()) {
            case WEB -> true;
            case CHAPTER -> open.isPresent() && !brokenCheck().test(destination);
            case MALFORMED -> false;
        };
    }

    /** Opens the chapter {@code target} names, unless it is {@code open}, and takes the author to its place. */
    private void openChapter(Chapter open, LinkTarget target) {

        Optional<Chapter> to = chapters.get().stream().filter(found -> found.title().equals(target.chapter()))
                .findFirst();
        to.filter(found -> !found.equals(open)).ifPresent(show);
        // The chapter may not have opened, as when its file cannot be read.
        if (to.isEmpty() || !to.equals(chapter.get()))
            return;

        manuscript.reveal(target.anchor().isEmpty() ? 0 : HeadingAnchors.start(manuscript.text(), target.anchor())
                .orElse(0));
    }

    private void checkAgainst(Map<String, Set<String>> anchors) {

        this.anchors = anchors;
        manuscript.setBookAnchors(anchors);
        // Dropped as the book closes, they leave nothing open to check again.
        if (anchors != null)
            onChecked.run();
    }
}
