package it.infog.psiche.git.model;

import it.infog.psiche.git.model.enums.ChapterChangeKind;

/**
 * One chapter as a commit changed it, with its whole text before and after the commit.
 *
 * @param path     the chapter's path in the repository after the commit; for a deleted chapter, the path it had
 * @param fromPath the chapter it was renamed or copied from; {@code null} unless {@code kind} is
 *                 {@link ChapterChangeKind#RENAMED} or {@link ChapterChangeKind#COPIED}
 * @param before   the text before the commit, of {@code fromPath} for a renamed or copied chapter; empty for a new one
 * @param after    the text after the commit; empty for a deleted chapter
 */
public record ChapterChange(String path,
                            String fromPath,
                            ChapterChangeKind kind,
                            String before,
                            String after) {
}
