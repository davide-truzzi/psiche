package it.infog.psiche.export;

import it.infog.psiche.export.model.ExportedChapter;
import it.infog.psiche.export.model.LinkPlace;
import it.infog.psiche.markdown.HeadingAnchors;
import it.infog.psiche.markdown.TextLinks;
import it.infog.psiche.markdown.model.LinkTarget;
import it.infog.psiche.markdown.model.enums.LinkKind;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Heading;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Where the links of exported chapters lead in the exported file. A link to a chapter of the export leads to its start,
 * a link to one of its headings to the heading; every format marks each heading with {@link #headingId} and each
 * chapter's start with its id. A link to a chapter that is not in the export, such as an empty chapter left out or
 * another chapter than the one exported alone, or to a heading that is not there, leads nowhere: the exports print its
 * text alone. Destinations are read and headings named as the editor and the preview read and name them.
 */
public final class LinkTargets {

    // Between a chapter's id and a heading's anchor, in the heading's id; anchors never start with it.
    private static final String HEADING_MARK = "--";

    // The id of each chapter in the export, by its file title.
    private final Map<String, String> ids = new HashMap<>();
    // The anchors of each chapter's headings, by its file title.
    private final Map<String, Set<String>> anchors = new HashMap<>();
    // True if each chapter's start is marked with its id, as in the whole book; a chapter alone has no such mark.
    private final boolean startsMarked;

    private LinkTargets(List<ExportedChapter> chapters, boolean startsMarked) {

        this.startsMarked = startsMarked;
        for (ExportedChapter chapter : chapters) {
            ids.put(chapter.fileTitle(), chapter.id());
            anchors.put(chapter.fileTitle(), anchors(chapter));
        }
    }

    /** The links of the whole book's {@code chapters}, each of which starts with a mark of its id. */
    public static LinkTargets of(List<ExportedChapter> chapters) {

        return new LinkTargets(chapters, true);
    }

    /** The links of {@code chapter}, exported alone: only those to its own headings lead somewhere. */
    public static LinkTargets alone(ExportedChapter chapter) {

        return new LinkTargets(List.of(chapter), false);
    }

    /** The id of the heading at {@code anchor} in the chapter whose id is {@code chapterId}, unique in the book. */
    public static String headingId(String chapterId, String anchor) {

        return chapterId + HEADING_MARK + anchor;
    }

    /** True if {@code destination} is a web or mail address, which every export keeps as a link. */
    public static boolean isWeb(String destination) {

        return TextLinks.target(destination, "").kind() == LinkKind.WEB;
    }

    /** Where a link of {@code from} found at {@code destination} leads in the export; empty if it leads nowhere. */
    public Optional<LinkPlace> place(ExportedChapter from, String destination) {

        LinkTarget target = TextLinks.target(destination, from.fileTitle());
        String id = ids.get(target.chapter());
        if (target.kind() != LinkKind.CHAPTER || id == null)
            return Optional.empty();

        if (target.anchor().isEmpty())
            return startsMarked ? Optional.of(new LinkPlace(id, id)) : Optional.empty();

        return anchors.get(target.chapter()).contains(target.anchor())
                ? Optional.of(new LinkPlace(id, headingId(id, target.anchor())))
                : Optional.empty();
    }

    /** The anchors of {@code chapter}'s headings, named in order as the renderers name them. */
    private static Set<String> anchors(ExportedChapter chapter) {

        Set<String> found = new HashSet<>();
        Function<Heading, String> namer = HeadingAnchors.namer();
        chapter.document().accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {

                found.add(namer.apply(heading));
            }
        });

        return found;
    }
}
