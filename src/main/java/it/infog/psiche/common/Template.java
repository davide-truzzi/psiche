package it.infog.psiche.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A file the app fills in, kept as a resource with placeholders: a part of an exported document, such as an ODT's
 * {@code styles.xml}, the preview's page, or a script the preview runs.
 */
public final class Template {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([\\w-]+)}}");

    private final String text;

    private Template(String text) {

        this.text = text;
    }

    /** Reads the resource {@code name}, such as {@code styles.xml}, from the package of {@code owner}. */
    public static Template load(Class<?> owner, String name) {

        try (InputStream in = owner.getResourceAsStream(name)) {
            if (in == null)
                throw new IllegalStateException("Missing resource " + name);

            return new Template(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + name, e);
        }
    }

    /**
     * The part with each {@code {{name}}} replaced by its value, in one pass, so values are never searched for
     * placeholders: a chapter's text may well contain {@code {{header}}}. A placeholder without a value stays as it is.
     *
     * @param values XML by placeholder name; text in them must already be escaped for XML
     */
    public String fill(Map<String, String> values) {

        Matcher placeholder = PLACEHOLDER.matcher(text);
        return placeholder.replaceAll(match -> Matcher.quoteReplacement(values.getOrDefault(match.group(1),
                match.group())));
    }
}
