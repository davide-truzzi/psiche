package it.infog.psiche.book;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BookLinksTest {

    @TempDir
    Path tempDir;

    @Test
    void aRenameRewritesTheLinksToTheChapterInEveryChapterAndOnlyThose() {

        BookFolder book = BookFolder.create(tempDir, "The Salt Road", "en-US", "");
        Chapter departure = book.createChapter("01-departure");
        Chapter harbour = book.createChapter("02-harbour");
        Chapter sea = book.createChapter("03-sea");
        book.write(departure, "Go to [the storm](02-harbour.md#the-storm).\n");
        book.write(harbour, "# Harbour\n\n## The storm\n\nBack [up](02-harbour.md) or [here](#the-storm).\n");
        book.write(sea, "Nothing about it, only [the start](01-departure.md).\n");
        Chapter renamed = book.renameChapter(harbour, "02 The harbour");

        assertThat(BookLinks.followRename(book, "02-harbour", "02 The harbour")).containsExactly(departure, renamed);

        assertThat(book.read(departure)).isEqualTo("Go to [the storm](<02 The harbour.md#the-storm>).\n");
        assertThat(book.read(renamed))
                .isEqualTo("# Harbour\n\n## The storm\n\nBack [up](<02 The harbour.md>) or [here](#the-storm).\n");
        assertThat(book.read(sea)).isEqualTo("Nothing about it, only [the start](01-departure.md).\n");
    }
}
