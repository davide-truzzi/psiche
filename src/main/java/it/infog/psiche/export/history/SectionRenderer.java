package it.infog.psiche.export.history;

import it.infog.psiche.export.history.model.CountedRevision;
import it.infog.psiche.export.history.model.HistorySection;
import it.infog.psiche.export.history.model.Passage;
import it.infog.psiche.git.TrackedChanges;
import it.infog.psiche.git.model.BookRevision;
import it.infog.psiche.git.model.ChapterChange;
import it.infog.psiche.git.model.Comparison;
import it.infog.psiche.git.model.Paragraph;
import it.infog.psiche.git.model.Segment;
import it.infog.psiche.git.model.enums.ChapterChangeKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static it.infog.psiche.export.XmlText.escape;

/**
 * Writes one section of the writing history as XHTML: when it was made, its message and how much it changed, then
 * the passages it changed in each chapter as tracked changes, written text in {@code <ins>} and removed text in
 * {@code <del>}. A run of small revisions shows only its largest passages.
 */
final class SectionRenderer {

    // The passages a run of small revisions shows, its largest ones.
    static final int NOTABLE = 3;
    private static final String GAP = "<p class=\"gap\">[…]</p>\n";
    private static final String CHAPTER_EXTENSION = ".md";

    private final HistoryDates dates;

    SectionRenderer(HistoryDates dates) {

        this.dates = dates;
    }

    /** The section's anchor, for the bookmarks and the summary's links. */
    static String id(HistorySection section) {

        return "revision-" + section.first().number();
    }

    /** As "Revision 12", or "Revisions 14–19" for a run. */
    static String name(HistorySection section) {

        return section.grouped()
                ? "Revisions %d–%d".formatted(section.first().number(), section.last().number())
                : "Revision %d".formatted(section.first().number());
    }

    /** The chapter's file name without its folder and extension, as the author named the chapter. */
    static String chapterTitle(String path) {

        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.endsWith(CHAPTER_EXTENSION) ? name.substring(0, name.length() - CHAPTER_EXTENSION.length()) : name;
    }

    String render(HistorySection section) {

        StringBuilder html = new StringBuilder("<section class=\"revision\" id=\"%s\">\n".formatted(id(section)));
        html.append("<h2>%s</h2>\n".formatted(name(section)));
        if (section.grouped())
            run(section, html);
        else
            revision(section, html);

        return html.append("</section>\n").toString();
    }

    /** A revision of its own: every passage it changed. */
    private void revision(HistorySection section, StringBuilder html) {

        CountedRevision counted = section.first();
        BookRevision revision = counted.revision();
        html.append(paragraph("when", "%s · commit <code>%s</code>".formatted(dates.dayAndTime(revision.time()),
                escape(revision.commitId()))));

        //the author's message, every line of it
        html.append("<div class=\"message\">\n");
        revision.message().lines().filter(line -> !line.isBlank())
                .forEach(line -> html.append(paragraph("", escape(line))));
        html.append("</div>\n");

        if (!revision.merge())
            html.append(paragraph("counts", counts(counted)));
        else if (revision.changes().isEmpty())
            html.append(paragraph("counts", "Merged the work of another computer, with no changes of its own."));
        else
            html.append(paragraph("counts", "Merged the work of another computer, with these changes of its own:"));

        for (ChapterChange change : section.changes()) {
            html.append(heading(change));
            if (change.kind() == ChapterChangeKind.DELETED)
                html.append(paragraph("note", "Deleted from the book."));
            else if (change.before().equals(change.after()))
                html.append(paragraph("note", "Its text did not change."));
            else
                allPassages(TrackedChanges.compare(change.before(), change.after()), html);
        }
    }

    private void allPassages(Comparison comparison, StringBuilder html) {

        List<Passage> passages = Passages.of(comparison);
        if (passages.isEmpty())
            html.append(paragraph("note", "Only spacing changed."));
        else
            passages(comparison, passages, html);
    }

    /** A run of small revisions: what it changed, and its {@link #NOTABLE} largest passages. */
    private void run(HistorySection section, StringBuilder html) {

        BookRevision first = section.first().revision();
        BookRevision last = section.last().revision();
        html.append(paragraph("when", "%s · commits <code>%s</code> to <code>%s</code>".formatted(
                dates.span(first.time(), last.time()), escape(first.shortId()), escape(last.shortId()))));
        html.append(paragraph("counts", runCounts(section)));

        //every passage of the run, to pick the largest
        List<Found> found = new ArrayList<>();
        List<ChapterChange> changes = section.changes();
        for (int i = 0; i < changes.size(); i++) {
            ChapterChange change = changes.get(i);
            if (change.kind() == ChapterChangeKind.DELETED || change.before().equals(change.after()))
                continue;

            Comparison comparison = TrackedChanges.compare(change.before(), change.after());
            for (Passage passage : Passages.of(comparison))
                found.add(new Found(i, comparison, passage));
        }

        List<Found> notable = found.stream()
                .sorted(Comparator.comparingInt((Found f) -> f.passage().words()).reversed())
                .limit(NOTABLE)
                .sorted(Comparator.comparingInt(Found::chapter).thenComparingInt(f -> f.passage().from()))
                .toList();
        if (found.size() > NOTABLE)
            html.append(paragraph("", "Most notably:"));

        //the picked passages, under their chapters
        for (int i = 0; i < changes.size(); i++) {
            int chapter = i;
            List<Found> inChapter = notable.stream().filter(f -> f.chapter() == chapter).toList();
            if (inChapter.isEmpty())
                continue;

            html.append(heading(changes.get(i)));
            passages(inChapter.getFirst().comparison(), inChapter.stream().map(Found::passage).toList(), html);
        }

        int smaller = found.size() - notable.size();
        if (smaller > 0)
            html.append(paragraph("more", "…and %d smaller %s.".formatted(smaller,
                    smaller == 1 ? "change" : "changes")));
    }

    /** The passages of one chapter, with […] wherever text is left out between them or around them. */
    private static void passages(Comparison comparison, List<Passage> passages, StringBuilder html) {

        List<Paragraph> paragraphs = comparison.paragraphs();
        int next = 0;
        html.append("<div class=\"passages\">\n");
        for (Passage passage : passages) {
            if (Passages.textBetween(paragraphs, next, passage.from()) > 0)
                html.append(GAP);

            paragraphs.subList(passage.from(), passage.to()).stream().filter(Passages::hasText)
                    .forEach(line -> html.append(line(line)));
            next = passage.to();
        }

        if (Passages.textBetween(paragraphs, next, paragraphs.size()) > 0)
            html.append(GAP);
        html.append("</div>\n");
    }

    /** One line of the chapter's Markdown, as the author typed it, with what changed marked. */
    private static String line(Paragraph paragraph) {

        StringBuilder html = new StringBuilder("<p class=\"line\">");
        for (Segment segment : paragraph.segments()) {
            String text = escape(segment.text());
            switch (segment.change()) {
                case UNCHANGED -> html.append(text);
                case ADDED -> html.append("<ins>").append(text).append("</ins>");
                case REMOVED -> html.append("<del>").append(text).append("</del>");
            }
        }

        return html.append("</p>\n").toString();
    }

    /** The chapter's title, with what happened to it unless it was only edited. */
    private static String heading(ChapterChange change) {

        String note = note(change);
        return "<h3>%s%s</h3>\n".formatted(escape(chapterTitle(change.path())),
                note.isEmpty() ? "" : " <span class=\"note\">(%s)</span>".formatted(escape(note)));
    }

    /** What happened to the chapter, as "new" or "renamed from Lei"; empty if it was only edited. */
    private static String note(ChapterChange change) {

        return switch (change.kind()) {
            case ADDED -> "new";
            case CHANGED -> "";
            case RENAMED -> "renamed from " + chapterTitle(change.fromPath());
            case COPIED -> "copied from " + chapterTitle(change.fromPath());
            case DELETED -> "deleted";
        };
    }

    /** As "Wrote 120 words and removed 30." */
    private static String counts(CountedRevision revision) {

        int added = revision.wordsAdded();
        int removed = revision.wordsRemoved();
        if (added == 0 && removed == 0)
            return "No words written or removed.";
        if (removed == 0)
            return "Wrote %s.".formatted(words(added));
        if (added == 0)
            return "Removed %s.".formatted(words(removed));

        return "Wrote %s and removed %d.".formatted(words(added), removed);
    }

    /** As "6 small revisions changed 212 words in 3 chapters (Il porto, Marta (renamed from Lei))." */
    private static String runCounts(HistorySection section) {

        int revisions = section.revisions().size();
        List<ChapterChange> changes = section.changes();
        if (changes.isEmpty())
            return "%d small revisions, whose changes cancel each other out.".formatted(revisions);

        List<String> chapters = changes.stream().map(change -> {
            String note = note(change);
            String title = "<em>%s</em>".formatted(escape(chapterTitle(change.path())));
            return note.isEmpty() ? title : "%s (%s)".formatted(title, escape(note));
        }).toList();

        return "%d small revisions changed %s in %d %s: %s.".formatted(revisions, words(section.wordsChanged()),
                changes.size(), changes.size() == 1 ? "chapter" : "chapters", String.join(", ", chapters));
    }

    private static String words(int count) {

        return count == 1 ? "1 word" : count + " words";
    }

    /** A paragraph of the section's own text, already XHTML, with {@code style} as its class if not empty. */
    private static String paragraph(String style, String xhtml) {

        return style.isEmpty() ? "<p>%s</p>\n".formatted(xhtml) : "<p class=\"%s\">%s</p>\n".formatted(style, xhtml);
    }

    /** A passage of the run, in its chapter's comparison. */
    private record Found(int chapter,
                         Comparison comparison,
                         Passage passage) {
    }
}
