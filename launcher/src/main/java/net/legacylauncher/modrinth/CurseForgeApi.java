package net.legacylauncher.modrinth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
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
import java.util.Collections;
import java.util.List;

/**
 * Thin read-only client for the CurseForge v1 API.
 * <p>
 * Unlike Modrinth, CurseForge answers nothing without an API key - every request comes
 * back 403 - and keys are issued per application from their developer console. The key is
 * therefore supplied by whoever runs the launcher rather than baked in here.
 */
@Slf4j
public final class CurseForgeApi {
    public static final String BASE_URL = "https://api.curseforge.com/v1";

    /**
     * Where a user gets a key of their own.
     */
    public static final String CONSOLE_URL = "https://console.curseforge.com/";

    /**
     * CurseForge's id for Minecraft.
     */
    private static final int GAME_ID = 432;

    private static final Gson GSON = new GsonBuilder().create();

    private CurseForgeApi() {
    }

    /**
     * The "class" ids CurseForge files content under.
     */
    static int classIdOf(ContentType type) {
        switch (type) {
            case RESOURCE_PACK:
                return 12;
            case SHADER:
                return 6552;
            case DATA_PACK:
                return 6945;
            case MOD:
            default:
                return 6;
        }
    }

    /**
     * CurseForge numbers the loaders instead of naming them.
     */
    static Integer loaderTypeOf(ModLoader loader) {
        if (loader == null) {
            return null;
        }
        switch (loader) {
            case FORGE:
                return 1;
            case FABRIC:
                return 4;
            case QUILT:
                return 5;
            case NEOFORGE:
                return 6;
            default:
                return null;
        }
    }

    public static SearchResponse search(String apiKey, ContentType type, String query,
                                        String gameVersion, ModLoader loader,
                                        int sortField, int offset, int limit) throws IOException {
        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/mods/search?gameId=").append(GAME_ID)
                .append("&classId=").append(classIdOf(type))
                .append("&index=").append(offset)
                .append("&pageSize=").append(limit)
                .append("&sortField=").append(sortField)
                .append("&sortOrder=desc");
        if (StringUtils.isNotEmpty(query)) {
            url.append("&searchFilter=").append(encode(query));
        }
        if (StringUtils.isNotEmpty(gameVersion)) {
            url.append("&gameVersion=").append(encode(gameVersion));
        }
        Integer loaderType = type.isLoaderSpecific() ? loaderTypeOf(loader) : null;
        if (loaderType != null) {
            url.append("&modLoaderType=").append(loaderType);
        }
        return parse(get(apiKey, url.toString()), SearchResponse.class, "search result");
    }

    /**
     * Lists the files of one project that fit the given game version and loader.
     */
    public static FilesResponse listFiles(String apiKey, ContentType type, String projectId,
                                          String gameVersion, ModLoader loader) throws IOException {
        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/mods/").append(encode(projectId)).append("/files?pageSize=50");
        if (StringUtils.isNotEmpty(gameVersion)) {
            url.append("&gameVersion=").append(encode(gameVersion));
        }
        Integer loaderType = type.isLoaderSpecific() ? loaderTypeOf(loader) : null;
        if (loaderType != null) {
            url.append("&modLoaderType=").append(loaderType);
        }
        return parse(get(apiKey, url.toString()), FilesResponse.class, "file list");
    }

    /**
     * Fetches one specific file, used to follow dependencies.
     */
    public static FileResponse getFile(String apiKey, long projectId, long fileId) throws IOException {
        String url = BASE_URL + "/mods/" + projectId + "/files/" + fileId;
        return parse(get(apiKey, url), FileResponse.class, "file");
    }

    /**
     * @return the Minecraft versions CurseForge knows about, newest first
     */
    public static VersionTypesResponse listGameVersions(String apiKey) throws IOException {
        return parse(get(apiKey, BASE_URL + "/games/" + GAME_ID + "/versions"),
                VersionTypesResponse.class, "game versions");
    }

    private static String get(String apiKey, String url) throws IOException {
        if (StringUtils.isEmpty(apiKey)) {
            throw new ModrinthException("no CurseForge API key is set");
        }
        log.debug("GET {}", url);
        final String body;
        try {
            body = EHttpClient.toString(
                    Request.get(url)
                            .addHeader(HttpHeaders.ACCEPT, "application/json")
                            .addHeader(HttpHeaders.USER_AGENT, LauncherUserAgent.USER_AGENT)
                            .addHeader("x-api-key", apiKey)
            );
        } catch (IOException e) {
            throw new ModrinthException("could not reach CurseForge: " + e.getMessage(), e);
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
            throw new ModrinthException("could not read " + what + " sent by CurseForge", e);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is always supported", e);
        }
    }

    // ------------------------------------------------------------ responses

    public static class SearchResponse {
        public List<Mod> data;
        public Pagination pagination;

        public List<Mod> mods() {
            return data == null ? Collections.<Mod>emptyList() : data;
        }
    }

    public static class Pagination {
        public int index;
        public int resultCount;
        public int totalCount;
    }

    public static class Mod {
        public long id;
        public String name;
        public String summary;
        public String slug;
        @SerializedName("downloadCount")
        public double downloadCount;
        public Logo logo;
        public List<Author> authors;
        public List<Category> categories;
        public Links links;
    }

    public static class Logo {
        public String thumbnailUrl;
        public String url;
    }

    public static class Author {
        public String name;
    }

    public static class Category {
        public String name;
    }

    public static class Links {
        public String websiteUrl;
    }

    public static class FilesResponse {
        public List<ModFile> data;
        public Pagination pagination;

        public List<ModFile> files() {
            return data == null ? Collections.<ModFile>emptyList() : data;
        }
    }

    public static class FileResponse {
        public ModFile data;
    }

    public static class ModFile {
        public long id;
        public long modId;
        public String displayName;
        public String fileName;
        /**
         * 1 = release, 2 = beta, 3 = alpha.
         */
        public int releaseType;
        public String downloadUrl;
        public long fileLength;
        public List<Hash> hashes;
        public List<Dependency> dependencies;

        public String hash(int algo) {
            if (hashes == null) {
                return null;
            }
            for (Hash hash : hashes) {
                if (hash.algo == algo) {
                    return hash.value;
                }
            }
            return null;
        }
    }

    /**
     * CurseForge publishes only sha1 (1) and md5 (2).
     */
    public static class Hash {
        public String value;
        public int algo;
    }

    public static class Dependency {
        public long modId;
        /**
         * 3 = required, everything else is optional, incompatible or embedded.
         */
        public int relationType;
    }

    public static class VersionTypesResponse {
        public List<VersionType> data;

        public List<VersionType> types() {
            return data == null ? Collections.<VersionType>emptyList() : data;
        }
    }

    public static class VersionType {
        public int type;
        public List<String> versions;
    }
}
