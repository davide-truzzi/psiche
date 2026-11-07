package it.infog.psiche.git;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The book's {@code .gitignore}, each change to it committed alone. */
class GitIgnoreTest extends GitBookTest {

    @Test
    void ignoringAPatternCommitsGitignoreAloneAndOnlyOnce() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        commitAll("First draft");
        write("chapters/01-departure.md", DEPARTURE + "A new line, not committed.\n");

        assertThat(repository.gitignore().has("notes/")).isFalse();
        assertThat(repository.gitignore().add("notes/", "Keep chapter notes out of Git")).isTrue();
        assertThat(repository.gitignore().has("notes/")).isTrue();
        assertThat(repository.gitignore().add("notes/", "Keep chapter notes out of Git")).isFalse();

        assertThat(bookRoot.resolve(".gitignore")).hasContent("notes/");
        assertThat(git.log().call()).extracting(RevCommit::getShortMessage)
                .containsExactly("Keep chapter notes out of Git", "First draft");
        assertThat(git.status().call().getModified()).containsExactly("chapters/01-departure.md");
    }

    @Test
    void trackingBringsTheImagesInButNotTheDeletedOnesNorOtherWork() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        write("media/book.properties", "title=Alba\n");
        write(".gitignore", "notes/\nmedia/*/\n");
        commitAll("First draft");
        write("media/01-departure/map.png", "map");
        write("media/03-la-fine-del-mondo/sea.png", "sea");
        write("media/01-departure-del-2026-11-07-10-00-00/old.png", "old");
        write("media/book.properties", "title=Alba, changed\n");
        write("chapters/02-crossing.md", "Not committed.\n");

        boolean tracked = repository.gitignore().track("media/*/", "media/*-del-[0-9]*/", bookRoot.resolve("media"),
                "Keep chapter images in Git");

        assertThat(tracked).isTrue();
        assertThat(Files.readString(bookRoot.resolve(".gitignore"))).isEqualTo("notes/\nmedia/*-del-[0-9]*/\n");
        RevCommit last = git.log().setMaxCount(1).call().iterator().next();
        assertThat(last.getShortMessage()).isEqualTo("Keep chapter images in Git");
        assertThat(filesIn(last)).containsExactlyInAnyOrder(".gitignore", "chapters/01-departure.md",
                "media/book.properties", "media/01-departure/map.png", "media/03-la-fine-del-mondo/sea.png");
        assertThat(git.status().call().getModified()).containsExactly("media/book.properties");
        assertThat(git.status().call().getUntracked()).containsExactly("chapters/02-crossing.md");
        assertThat(repository.gitignore().track("media/*/", "media/*-del-[0-9]*/", bookRoot.resolve("media"), "Again"))
                .isFalse();
    }

    @Test
    void anIgnoredPatternGoesOnALineOfItsOwn() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);
        write(".gitignore", "*.bak");
        commitAll("First draft");

        repository.gitignore().add("notes/", "Keep chapter notes out of Git");

        assertThat(Files.readString(bookRoot.resolve(".gitignore"))).isEqualTo("*.bak\nnotes/\n");
    }

    @Test
    void aBookWithoutCommitsCanIgnoreAPattern() throws Exception {
        write("chapters/01-departure.md", DEPARTURE);

        assertThat(repository.gitignore().add("notes/", "Keep chapter notes out of Git")).isTrue();

        assertThat(git.log().call()).extracting(RevCommit::getShortMessage)
                .containsExactly("Keep chapter notes out of Git");
        assertThat(git.status().call().getUntracked()).containsExactly("chapters/01-departure.md");
    }

    private List<String> filesIn(RevCommit commit) throws IOException {
        List<String> files = new ArrayList<>();
        try (var walk = new TreeWalk(git.getRepository())) {
            walk.addTree(commit.getTree());
            walk.setRecursive(true);
            while (walk.next())
                files.add(walk.getPathString());
        }
        return files;
    }
}
