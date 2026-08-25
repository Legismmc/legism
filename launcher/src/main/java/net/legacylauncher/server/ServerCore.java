package net.legacylauncher.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.EHttpClient;
import net.legacylauncher.util.ua.LauncherUserAgent;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpHeaders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A downloadable server implementation. Each one knows how to list the Minecraft versions
 * it publishes a build for, and how to resolve one of those versions to an actual jar to
 * download - two very different APIs per core, hidden behind the same two methods.
 * <p>
 * Every method here blocks on network I/O, so callers must stay off the Swing thread.
 */
@Slf4j
public enum ServerCore {
    VANILLA {
        private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

        @Override
        public List<String> fetchVersions() throws IOException {
            JsonObject manifest = getJson(MANIFEST_URL);
            List<String> versions = new ArrayList<>();
            for (JsonElement e : manifest.getAsJsonArray("versions")) {
                JsonObject v = e.getAsJsonObject();
                if ("release".equals(v.get("type").getAsString())) {
                    versions.add(v.get("id").getAsString());
                }
            }
            return versions;
        }

        @Override
        public ServerCoreDownload resolveDownload(String mcVersion) throws IOException {
            JsonObject manifest = getJson(MANIFEST_URL);
            String versionUrl = null;
            for (JsonElement e : manifest.getAsJsonArray("versions")) {
                JsonObject v = e.getAsJsonObject();
                if (mcVersion.equals(v.get("id").getAsString())) {
                    versionUrl = v.get("url").getAsString();
                    break;
                }
            }
            if (versionUrl == null) {
                throw new IOException("Unknown Minecraft version: " + mcVersion);
            }
            JsonObject version = getJson(versionUrl);
            JsonObject downloads = asObjectOrNull(version, "downloads");
            JsonObject server = downloads != null ? asObjectOrNull(downloads, "server") : null;
            String url = server != null ? optString(server, "url") : null;
            if (url == null) {
                // versions old enough predate a standalone server download in their
                // manifest entry (pre-1.2.5 or so) - nothing this core can install
                throw new IOException("No server download published for Minecraft " + mcVersion);
            }
            String sha1 = optString(server, "sha1");
            return new ServerCoreDownload(url, "server.jar", sha1 != null ? "SHA-1" : null, sha1);
        }
    },

    PAPER {
        private static final String API = "https://api.papermc.io/v2/projects/paper";

        @Override
        public List<String> fetchVersions() throws IOException {
            JsonObject project = getJson(API);
            List<String> versions = toStringList(project.getAsJsonArray("versions"));
            Collections.reverse(versions); // PaperMC lists oldest first
            return versions;
        }

        @Override
        public ServerCoreDownload resolveDownload(String mcVersion) throws IOException {
            JsonObject builds = getJson(API + "/versions/" + mcVersion + "/builds");
            JsonArray buildArray = builds.getAsJsonArray("builds");
            if (buildArray == null || buildArray.isEmpty()) {
                throw new IOException("No Paper builds published for " + mcVersion);
            }
            JsonObject latest = buildArray.get(buildArray.size() - 1).getAsJsonObject();
            int build = latest.get("build").getAsInt();
            JsonObject application = latest.getAsJsonObject("downloads").getAsJsonObject("application");
            String fileName = application.get("name").getAsString();
            String url = API + "/versions/" + mcVersion + "/builds/" + build + "/downloads/" + fileName;
            // the API has published sha256 both directly on "application" and nested under
            // an "application.checksums" object across different versions - accept either
            String sha256 = optString(application, "sha256");
            if (sha256 == null) {
                JsonObject checksums = asObjectOrNull(application, "checksums");
                sha256 = checksums != null ? optString(checksums, "sha256") : null;
            }
            return new ServerCoreDownload(url, fileName, sha256 != null ? "SHA-256" : null, sha256);
        }
    },

    PURPUR {
        private static final String API = "https://api.purpurmc.org/v2/purpur";

        @Override
        public List<String> fetchVersions() throws IOException {
            JsonObject project = getJson(API);
            List<String> versions = toStringList(project.getAsJsonArray("versions"));
            Collections.reverse(versions); // PurpurMC lists oldest first
            return versions;
        }

        @Override
        public ServerCoreDownload resolveDownload(String mcVersion) throws IOException {
            JsonObject latest = getJson(API + "/" + mcVersion + "/latest");
            String build = latest.get("build").getAsString();
            String url = API + "/" + mcVersion + "/" + build + "/download";
            // published either as a flat "md5" string, or as {"md5": {"application": "..."}}
            // depending on API version - accept either rather than guess wrong and crash
            String md5 = optString(latest, "md5");
            if (md5 == null) {
                JsonObject md5s = asObjectOrNull(latest, "md5");
                md5 = md5s != null ? optString(md5s, "application") : null;
            }
            return new ServerCoreDownload(url, "purpur-" + mcVersion + ".jar", md5 != null ? "MD5" : null, md5);
        }
    };

    /**
     * @return every Minecraft version this core publishes a build for, newest first
     */
    public abstract List<String> fetchVersions() throws IOException;

    /**
     * Resolves the actual jar to download for one Minecraft version.
     */
    public abstract ServerCoreDownload resolveDownload(String mcVersion) throws IOException;

    /**
     * Whether servers built on this core can load Bukkit/Spigot-family plugins - Vanilla
     * cannot, so the plugin browser is hidden for it.
     */
    public boolean supportsPlugins() {
        return this != VANILLA;
    }

    @Override
    public String toString() {
        String lower = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    static JsonObject getJson(String url) throws IOException {
        String body = EHttpClient.toString(
                Request.get(url).addHeader(HttpHeaders.USER_AGENT, LauncherUserAgent.USER_AGENT)
        );
        if (body == null) {
            throw new IOException("No response from " + url);
        }
        return JsonParser.parseString(body).getAsJsonObject();
    }

    /**
     * @return the member as a string, or {@code null} if it is absent or not a primitive -
     * never throws, unlike {@link JsonObject#get} chained straight into {@code getAsString()}
     */
    static String optString(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /**
     * @return the member as an object, or {@code null} if it is absent or not an object -
     * never throws, unlike {@link JsonObject#getAsJsonObject}
     */
    static JsonObject asObjectOrNull(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    static List<String> toStringList(JsonArray array) {
        List<String> result = new ArrayList<>();
        if (array != null) {
            for (JsonElement e : array) {
                result.add(e.getAsString());
            }
        }
        return result;
    }
}
