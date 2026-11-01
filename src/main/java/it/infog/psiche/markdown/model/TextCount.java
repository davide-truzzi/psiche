package it.infog.psiche.markdown.model;

/**
 * How long a chapter is as a reader sees it.
 *
 * @param characters the characters of the text, spaces included, as publishers count them for a cartella or feuillet
 * @param words      the words, each a run of characters between spaces with at least one letter or digit
 */
public record TextCount(int characters,
                        int words) {
}
