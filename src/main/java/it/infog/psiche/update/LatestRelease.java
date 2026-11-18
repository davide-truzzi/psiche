package it.infog.psiche.update;

import it.infog.psiche.update.exceptions.UpdateCheckException;
import it.infog.psiche.update.model.Release;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.module.ModuleDescriptor.Version;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks psiche.app for the latest release of Psiche Writer, and tells whether it is newer than the app's own version.
 * The site answers from a cache of its own, so a check never reaches GitHub.
 */
public final class LatestRelease {

    private static final Logger log = LoggerFactory.getLogger(LatestRelease.class);

    private static final URI SERVICE = URI.create("https://psiche.app/api/latest-release");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** A string field of the answer's JSON, its escapes kept; the fields read are unique to its top level. */
    private static final String FIELD = "\"%s\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"";
    private static final Pattern ESCAPE = Pattern.compile("\\\\(.)");
    private static final String TAG_PREFIX = "v";

    private static final String UNREACHABLE = "psiche.app could not be reached. Check the internet connection and "
            + "try again.";
    private static final String REFUSED = "psiche.app could not answer (HTTP %d). Try again later.";
    private static final String UNREADABLE = "psiche.app's answer could not be read.";

    private LatestRelease() {
    }

    /**
     * Asks the site, in the background, for the latest release.
     *
     * @param appVersion the app's own version, as the build writes it ("0.9.1-beta"), to name the app to the site
     * @return the release; fails with an {@link UpdateCheckException} when the site could not be reached or read
     */
    public static CompletableFuture<Release> fetch(String appVersion) {

        HttpRequest request = HttpRequest.newBuilder(SERVICE)
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "PsicheWriter/" + appVersion)
                .GET()
                .build();

        return CLIENT.sendAsync(request, BodyHandlers.ofString())
                .<HttpResponse<String>>exceptionally(error -> {
                    log.warn("The latest release could not be fetched", error);
                    throw new UpdateCheckException(UNREACHABLE, error);
                })
                .thenApply(LatestRelease::release);
    }

    /** True if {@code release} comes after {@code appVersion}, as the build writes it ("0.9.1-beta"). */
    public static boolean isNewer(Release release, String appVersion) {

        return version(release.tag()).compareTo(Version.parse(appVersion)) > 0;
    }

    /** The release the answer's JSON describes. */
    static Release parse(String json) {

        Release release = new Release(field(json, "version"), field(json, "tag"), field(json, "downloadUrl"));
        try {
            version(release.tag());
        } catch (IllegalArgumentException e) {
            throw new UpdateCheckException(UNREADABLE, e);
        }

        return release;
    }

    private static Release release(HttpResponse<String> response) {

        if (response.statusCode() != 200)
            throw new UpdateCheckException(REFUSED.formatted(response.statusCode()), null);

        return parse(response.body());
    }

    /** The version a tag names: "v0.9.1-beta" names 0.9.1-beta. */
    private static Version version(String tag) {

        return Version.parse(tag.startsWith(TAG_PREFIX) ? tag.substring(TAG_PREFIX.length()) : tag);
    }

    private static String field(String json, String name) {

        Matcher matcher = Pattern.compile(FIELD.formatted(name)).matcher(json);
        if (!matcher.find())
            throw new UpdateCheckException(UNREADABLE, null);

        return ESCAPE.matcher(matcher.group(1)).replaceAll("$1");
    }
}
