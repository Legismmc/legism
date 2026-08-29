package net.legacylauncher.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.configuration.BuildConfig;
import net.legacylauncher.util.EHttpClient;
import net.legacylauncher.util.ua.LauncherUserAgent;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpHeaders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Checks GitHub for a newer release of the launcher itself.
 * <p>
 * The build's own {@link net.legacylauncher.configuration.BuildConfig#VERSION} is an
 * internal build number ("1.169.4+tgsko"), unrelated to the "vX.Y.Z" tags releases are
 * published under - so there is no way to derive "is a newer release out" from it. Instead
 * {@link #CURRENT_TAG} carries the git tag of the commit this was built from, stamped in by
 * the build; it is empty for anything built off an untagged commit.
 * <p>
 * Every call blocks, so callers must stay off the Swing thread.
 */
@Slf4j
public final class SelfUpdateChecker {
    /**
     * The tag this build was released under, filled in at build time from the git tag on
     * the commit being built. Empty for anything built off a commit that is not tagged -
     * a development build has no release to compare itself against, so it does not ask.
     */
    private static final String CURRENT_TAG = BuildConfig.RELEASE_TAG;

    private static final String REPO = "Legismmc/legism";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String FALLBACK_URL = "https://github.com/" + REPO + "/releases/latest";

    private SelfUpdateChecker() {
    }

    public static final class LatestRelease {
        private final String tag;
        private final String url;
        private final List<Asset> assets;

        LatestRelease(String tag, String url, List<Asset> assets) {
            this.tag = tag;
            this.url = url;
            this.assets = assets == null ? Collections.emptyList() : assets;
        }

        public String getTag() {
            return tag;
        }

        public String getUrl() {
            return url;
        }

        /**
         * The files published with the release, in the order GitHub lists them.
         */
        public List<Asset> getAssets() {
            return assets;
        }
    }

    /**
     * One downloadable file from a release.
     */
    public static final class Asset {
        private final String name;
        private final String url;
        private final long size;

        Asset(String name, String url, long size) {
            this.name = name;
            this.url = url;
            this.size = size;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        /**
         * Size in bytes as GitHub reports it, for the progress bar and as a check that the
         * download arrived whole.
         */
        public long getSize() {
            return size;
        }
    }

    /**
     * @return the newest published release, if it differs from what is currently running;
     * empty when already current or the check could not complete (offline, GitHub
     * unreachable, unexpected response) - never something worth bothering the user with
     */
    public static Optional<LatestRelease> checkForUpdate() {
        if (CURRENT_TAG == null || CURRENT_TAG.trim().isEmpty()) {
            log.debug("Not a tagged build, skipping the update check");
            return Optional.empty();
        }
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
            return Optional.of(new LatestRelease(tag, url, readAssets(json)));
        } catch (Exception e) {
            log.debug("Could not check for a launcher update: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * Reads the release's file list. A release with no usable assets is not an error here:
     * the caller falls back to opening the release page, which is what happened for every
     * update before this.
     */
    private static List<Asset> readAssets(JsonObject json) {
        List<Asset> assets = new ArrayList<>();
        if (!json.has("assets") || !json.get("assets").isJsonArray()) {
            return assets;
        }
        for (JsonElement element : json.getAsJsonArray("assets")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject asset = element.getAsJsonObject();
            if (!asset.has("name") || !asset.has("browser_download_url")) {
                continue;
            }
            assets.add(new Asset(
                    asset.get("name").getAsString(),
                    asset.get("browser_download_url").getAsString(),
                    asset.has("size") ? asset.get("size").getAsLong() : -1L));
        }
        return assets;
    }
}
