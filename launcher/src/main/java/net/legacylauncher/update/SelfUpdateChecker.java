package net.legacylauncher.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.EHttpClient;
import net.legacylauncher.util.ua.LauncherUserAgent;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpHeaders;

import java.util.Optional;

/**
 * Checks GitHub for a newer release of the launcher itself.
 * <p>
 * The build's own {@link net.legacylauncher.configuration.BuildConfig#VERSION} is an
 * internal build number ("1.169.4+tgsko"), unrelated to the "vX.Y.Z" tags releases are
 * published under - so there is no way to derive "is a newer release out" from it. Instead
 * {@link #CURRENT_TAG} tracks the tag this source was last released as, and has to be bumped
 * by hand alongside every release (the same manual step {@code docs/index.html} already
 * needs).
 * <p>
 * Every call blocks, so callers must stay off the Swing thread.
 */
@Slf4j
public final class SelfUpdateChecker {
    /**
     * Bump this to match the git tag on every release.
     */
    private static final String CURRENT_TAG = "v1.4.1";

    private static final String REPO = "tgskoZ/legism";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String FALLBACK_URL = "https://github.com/" + REPO + "/releases/latest";

    private SelfUpdateChecker() {
    }

    public static final class LatestRelease {
        private final String tag;
        private final String url;

        LatestRelease(String tag, String url) {
            this.tag = tag;
            this.url = url;
        }

        public String getTag() {
            return tag;
        }

        public String getUrl() {
            return url;
        }
    }

    /**
     * @return the newest published release, if it differs from what is currently running;
     * empty when already current or the check could not complete (offline, GitHub
     * unreachable, unexpected response) - never something worth bothering the user with
     */
    public static Optional<LatestRelease> checkForUpdate() {
        try {
            String body = EHttpClient.toString(
                    Request.get(API_URL)
                            .addHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                            .addHeader(HttpHeaders.USER_AGENT, LauncherUserAgent.USER_AGENT)
            );
            if (body == null) {
                return Optional.empty();
            }
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("tag_name")) {
                return Optional.empty();
            }
            String tag = json.get("tag_name").getAsString();
            if (tag == null || tag.equals(CURRENT_TAG)) {
                return Optional.empty();
            }
            String url = json.has("html_url") ? json.get("html_url").getAsString() : FALLBACK_URL;
            return Optional.of(new LatestRelease(tag, url));
        } catch (Exception e) {
            log.debug("Could not check for a launcher update: {}", e.toString());
            return Optional.empty();
        }
    }
}
