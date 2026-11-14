package it.infog.psiche.git.model.enums;

/** What a commit did to one chapter file. */
public enum ChapterChangeKind {
    /** A new chapter: all its text was written in this commit. */
    ADDED,
    /** The chapter's text was edited under the same name. */
    CHANGED,
    /** The chapter got a new name, and maybe edits too. */
    RENAMED,
    /**
     * A new chapter made from the text of a chapter that the same commit renamed, so one chapter became two; maybe
     * edited too. A copy of a chapter still in the book shows as {@link #ADDED}: Git finds copies only this way.
     */
    COPIED,
    /** The chapter was removed from the book. */
    DELETED
}
