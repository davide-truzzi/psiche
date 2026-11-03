package it.infog.psiche.export.document.model.enums;

/**
 * Where the book starts a new page, and what changes there. Each format writes it in its own way: ODT in the style of
 * the new page's first paragraph, DOCX as the end of a section.
 */
public enum PageStart {

    /** The title page: the first page, in the front matter, without the header. */
    TITLE_PAGE,
    /** The contents: a new page, still in the front matter. */
    CONTENTS,
    /** The first chapter: back to the manuscript's pages, with the header, numbered from 1. */
    FIRST_CHAPTER,
    /** Any later chapter: a new page. */
    CHAPTER
}
