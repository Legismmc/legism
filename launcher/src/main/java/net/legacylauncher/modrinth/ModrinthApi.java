package net.legacylauncher.modrinth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.EHttpClient;
import net.legacylauncher.util.ua.LauncherUserAgent;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpHeaders;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Thin read-only client for the Modrinth v2 API.
 * <p>
 * Every call is blocking, so callers must stay off the Swing thread.
 */
@Slf4j
public final class ModrinthApi {
    public static final String BASE_URL = "https://api.modrinth.com/v2";

    /**
     * Modrinth asks for a descriptive User-Agent so they can contact the author of a
     * misbehaving client instead of silently blocking it.
     */
    private static final String USER_AGENT = LauncherUserAgent.USER_AGENT;

    private static final Gson GSON = new GsonBuilder().create();

    private static final Type VERSION_LIST = new TypeToken<List<ModrinthVersion>>() {
    }.getType();
    private static final Type GAME_VERSION_LIST = new TypeToken<List<GameVersion>>() {
    }.getType();
    private static final Type PROJECT_LIST = new TypeToken<List<ModrinthProject>>() {
    }.getType();
    private static final Type VERSION_FILES_MAP = new TypeToken<Map<String, ModrinthVersion>>() {
    }.getType();

    private ModrinthApi() {
    }

    /**
     * Searches the index for one kind of content.
     *
     * @param type        what to look for - mods, resource packs, shaders, data packs
     * @param query       free text, may be empty
     * @param gameVersion Minecraft version to restrict to, may be {@code null}
     * @param loader      mod loader id (fabric, forge, quilt, neoforge), may be {@code null};
     *                    ignored for types that are not loader specific, because a resource
     *                    pack works whatever the instance runs
     * @param sortIndex   one of relevance, downloads, follows, newest, updated
     */
    public static ModrinthSearchResult search(ContentType type,
                                              String query,
                                              String gameVersion,
                                              String loader,
                                              String sortIndex,
                                              int offset,
                                              int limit) throws ModrinthException {
        List<String> facets = new ArrayList<>();
        facets.add(facet("project_type", type.getModrinthType()));
        if (StringUtils.isNotEmpty(gameVersion)) {
            facets.add(facet("versions", gameVersion));
        }
        if (type.isLoaderSpecific() && StringUtils.isNotEmpty(loader)) {
            facets.add(facet("categories", loader));
        }

        StringBuilder url = new StringBuilder(BASE_URL).append("/search?limit=").append(limit)
                .append("&offset=").append(offset)
                .append("&index=").append(encode(StringUtils.isEmpty(sortIndex) ? "relevance" : sortIndex))
                .append("&facets=").append(encode("[" + StringUtils.join(facets, ",") + "]"));
        if (StringUtils.isNotEmpty(query)) {
            url.append("&query=").append(encode(query));
        }

        return parse(get(url.toString()), ModrinthSearchResult.class, "search result");
    }

    /**
     * Lists the versions of a project that fit the given game version and loader.
     * Modrinth returns them newest first.
     * <p>
     * The loader filter is only applied for loader specific content: shaders are tagged
     * with iris/optifine/canvas rather than with a mod loader, so filtering them by
     * "fabric" would hide every one of them.
     */
    public static List<ModrinthVersion> listVersions(ContentType type,
                                                     String projectIdOrSlug,
                                                     String gameVersion,
                                                     String loader) throws ModrinthException {
        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/project/").append(encode(projectIdOrSlug)).append("/version");
        List<String> query = new ArrayList<>();
        if (type.isLoaderSpecific() && StringUtils.isNotEmpty(loader)) {
            query.add("loaders=" + encode("[\"" + loader + "\"]"));
        }
        if (StringUtils.isNotEmpty(gameVersion)) {
            query.add("game_versions=" + encode("[\"" + gameVersion + "\"]"));
        }
        if (!query.isEmpty()) {
            url.append('?').append(StringUtils.join(query, '&'));
        }

        List<ModrinthVersion> versions = parse(get(url.toString()), VERSION_LIST, "version list");
        return versions == null ? Collections.<ModrinthVersion>emptyList() : versions;
    }

    /**
     * Fetches one specific version by its Modrinth id.
     */
    public static ModrinthVersion getVersion(String versionId) throws ModrinthException {
        return parse(get(BASE_URL + "/version/" + encode(versionId)), ModrinthVersion.class, "version");
    }

    /**
     * Identifies installed files by their SHA-1 hash, for spotting what a jar or zip
     * already sitting on disk actually is without the user having reinstalled it through
     * the launcher.
     *
     * @return every hash that matched, mapped to the version it belongs to; a hash with no
     * match on Modrinth is simply absent from the result
     */
    public static Map<String, ModrinthVersion> getVersionFiles(List<String> sha1Hashes) throws ModrinthException {
        if (sha1Hashes.isEmpty()) {
            return Collections.emptyMap();
        }
        JsonObject body = new JsonObject();
        JsonArray hashes = new JsonArray();
        for (String hash : sha1Hashes) {
            hashes.add(hash);
        }
        body.add("hashes", hashes);
        body.addProperty("algorithm", "sha1");
        Map<String, ModrinthVersion> result = parse(
                post(BASE_URL + "/version_files", body.toString()), VERSION_FILES_MAP, "version files");
        return result == null ? Collections.<String, ModrinthVersion>emptyMap() : result;
    }

    /**
     * Batch project lookup, for turning the project ids out of {@link #getVersionFiles}
     * into the titles and icons shown for installed content.
     */
    public static List<ModrinthProject> getProjects(List<String> ids) throws ModrinthException {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder idsJson = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                idsJson.append(',');
            }
            idsJson.append('"').append(ids.get(i).replace("\"", "")).append('"');
        }
        idsJson.append(']');
        List<ModrinthProject> result = parse(
                get(BASE_URL + "/projects?ids=" + encode(idsJson.toString())), PROJECT_LIST, "projects");
        return result == null ? Collections.<ModrinthProject>emptyList() : result;
    }

    /**
     * @return every Minecraft release known to Modrinth, newest first, snapshots excluded
     */
    public static List<String> listReleaseGameVersions() throws ModrinthException {
        List<GameVersion> all = parse(get(BASE_URL + "/tag/game_version"), GAME_VERSION_LIST, "game versions");
        List<String> releases = new ArrayList<>();
        if (all != null) {
            for (GameVersion version : all) {
                if ("release".equals(version.version_type) && version.version != null) {
                    releases.add(version.version);
                }
            }
        }
        // the endpoint's own ordering is not part of its contract, so sort here instead
        Collections.sort(releases, DESCENDING_VERSION);
        return releases;
    }

    /**
     * Orders 1.20.2 before 1.20 before 1.7.10, comparing the dot separated parts
     * numerically. Anything unparseable sorts last, alphabetically.
     */
    private static final Comparator<String> DESCENDING_VERSION = new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
            String[] left = a.split("\\.");
            String[] right = b.split("\\.");
            for (int i = 0; i < Math.max(left.length, right.length); i++) {
                int result = Integer.compare(part(right, i), part(left, i));
                if (result != 0) {
                    return result;
                }
            }
            return a.compareTo(b);
        }

        private int part(String[] parts, int index) {
            if (index >= parts.length) {
                return 0;
            }
            try {
                return Integer.parseInt(parts[index]);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    };

    private static String facet(String name, String value) {
        return "[\"" + name + ":" + value + "\"]";
    }

    private static String get(String url) throws ModrinthException {
        log.debug("GET {}", url);
        final String body;
        try {
            body = EHttpClient.toString(
                    Request.get(url)
                            .addHeader(HttpHeaders.ACCEPT, "application/json")
                            .addHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            );
        } catch (IOException e) {
            throw new ModrinthException("could not reach Modrinth: " + e.getMessage(), e);
        }
        if (body == null) {
            throw new ModrinthException("empty response from " + url);
        }
        return body;
    }

    private static String post(String url, String jsonBody) throws ModrinthException {
        log.debug("POST {}", url);
        final String body;
        try {
            body = EHttpClient.toString(
                    Request.post(url)
                            .bodyString(jsonBody, org.apache.hc.core5.http.ContentType.APPLICATION_JSON)
                            .addHeader(HttpHeaders.ACCEPT, "application/json")
                            .addHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            );
        } catch (IOException e) {
            throw new ModrinthException("could not reach Modrinth: " + e.getMessage(), e);
        }
        if (body == null) {
            throw new ModrinthException("empty response from " + url);
        }
        return body;
    }

    private static <T> T parse(String body, Type type, String what) throws ModrinthException {
        try {
            return GSON.fromJson(body, type);
        } catch (JsonSyntaxException e) {
            throw new ModrinthException("could not read " + what + " sent by Modrinth", e);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is always supported", e);
        }
    }

    /**
     * Shape of one entry of {@code /tag/game_version}.
     */
    private static class GameVersion {
        String version;
        String version_type;
    }
}
