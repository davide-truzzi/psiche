package it.infog.psiche.markdown.model;

/**
 * A link in a chapter's text, written inline: {@code [text](destination)}, or {@code [text](destination "title")}.
 *
 * @param range       where the whole link is in the text, from its opening bracket to its closing parenthesis
 * @param label       where the link's text is, between the brackets, as written, with its formatting and escapes
 * @param text        the link's text as a reader sees it
 * @param destination where the link leads, as written, such as {@code https://example.com} or
 *                    {@code 02-harbour.md#the-storm}
 * @param title       the title written after the destination, empty if none; Psiche writes none, but keeps it
 */
public record TextLink(TextRange range,
                       TextRange label,
                       String text,
                       String destination,
                       String title) {
}
