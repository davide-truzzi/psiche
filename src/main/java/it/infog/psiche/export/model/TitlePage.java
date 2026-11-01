package it.infog.psiche.export.model;

import it.infog.psiche.book.model.BookProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the book's title page shows, decided once for every format: the cover, then its lines in order. Each format
 * only decides how to draw a {@link Role}, so all of them show the same title page.
 *
 * @param cover the cover image, if the book has one
 * @param lines the lines below the cover, from the top
 */
public record TitlePage(Optional<Path> cover,
                        List<Line> lines) {

    /** What a line of the title page is, so each format can give it a look of its own. */
    public enum Role {
        TITLE,
        SUBTITLE,
        AUTHORS
    }

    /**
     * One line of the title page.
     *
     * @param role what the line is
     * @param text what it says, as plain text
     */
    public record Line(Role role,
                       String text) {
    }

    public TitlePage {

        lines = List.copyOf(lines);
    }

    /** The book's title, which also describes the cover to readers who cannot see it. */
    public String title() {

        return lines.stream().filter(line -> line.role() == Role.TITLE).map(Line::text).findFirst().orElse("");
    }

    /** The title page of a book: its cover, its title, then its subtitle and its authors if it has them. */
    public static TitlePage of(BookProperties properties, Optional<Path> cover) {

        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Role.TITLE, properties.title()));

        if (!properties.subtitle().isBlank())
            lines.add(new Line(Role.SUBTITLE, properties.subtitle()));
        if (!properties.authors().isEmpty())
            lines.add(new Line(Role.AUTHORS, String.join(", ", properties.authors())));

        return new TitlePage(cover, lines);
    }
}
