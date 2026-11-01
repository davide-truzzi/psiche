package it.infog.psiche.export.model;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.model.TitlePage.Line;
import it.infog.psiche.export.model.TitlePage.Role;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TitlePageTest {

    @Test
    void showsTheCoverThenTheTitleTheSubtitleAndTheAuthors() {
        BookProperties book = new BookProperties("La strada del sale", "Romanzo",
                List.of("Anna Bianchi", "Marco Rossi"), "it-IT", "", "urn:uuid:1234");
        Path cover = Path.of("media", "cover.jpg");

        TitlePage page = TitlePage.of(book, Optional.of(cover));

        assertThat(page.cover()).contains(cover);
        assertThat(page.title()).isEqualTo("La strada del sale");
        assertThat(page.lines()).containsExactly(
                new Line(Role.TITLE, "La strada del sale"),
                new Line(Role.SUBTITLE, "Romanzo"),
                new Line(Role.AUTHORS, "Anna Bianchi, Marco Rossi"));
    }

    @Test
    void leavesOutABlankSubtitleAndMissingAuthors() {
        BookProperties book = new BookProperties("La strada del sale", "  ", List.of(), "it-IT", "", "urn:uuid:1234");

        TitlePage page = TitlePage.of(book, Optional.empty());

        assertThat(page.cover()).isEmpty();
        assertThat(page.lines()).containsExactly(new Line(Role.TITLE, "La strada del sale"));
    }
}
