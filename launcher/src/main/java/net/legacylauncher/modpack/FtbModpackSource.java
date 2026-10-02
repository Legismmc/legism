package net.legacylauncher.modpack;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.modrinth.ContentProject;
import net.legacylauncher.modrinth.ContentSearchResult;
import net.legacylauncher.modrinth.CurseForgeProvider;
import net.legacylauncher.modrinth.ModLoader;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Feed The Beast's own packs, from the FTB modpacks API.
 * <p>
 * The API answers a search with bare pack ids and keeps everything else - names, art,
 * versions - behind one request per pack, so details are fetched a page at a time, a few
 * in parallel, and remembered for the rest of the session.
 * <p>
 * A version is a list of loose files, often thousands of them (every config file is its
 * own entry), each with a link and a hash; there is no archive to download.
 */
@Slf4j
public final class FtbModpackSource implements ModpackSource {
    public static final String ID = "ftb";

    private static final String API = "https://api.feed-the-beast.com/v1/modpacks/public/modpack/";
    private static final String CHANGELOG_API = "https://api.feed-the-beast.com/v1/modpacks/modpack/";
    private static final Gson GSON = new Gson();

    private final Map<Long, Pack> details = new ConcurrentHashMap<>();
    private volatile List<Long> allIds;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "FTB";
    }

    @Override
    public ContentSearchResult search(String query, String gameVersion, int offset, int limit) throws IOException {
        List<Long> ids;
        if (StringUtils.isBlank(query)) {
            ids = allIds();
        } else {
            IdList found = parse(PackDownloads.getText(API + "search/100?term=" + encode(query.trim())), IdList.class);
            ids = found == null || found.packs == null ? Collections.<Long>emptyList() : found.packs;
        }

        List<Pack> packs;
        if (gameVersion != null) {
            // there is no server-side version filter, so every candidate has to be looked at
            packs = new ArrayList<>();
            for (Pack pack : load(ids)) {
                if (pack.supports(gameVersion)) {
                    packs.add(pack);
                }
            }
            int from = Math.min(offset, packs.size());
            packs = packs.subList(from, Math.min(packs.size(), from + limit));
            return new ContentSearchResult(toProjects(packs), offset, countMatching(ids, gameVersion));
        }

        int from = Math.min(offset, ids.size());
        packs = load(ids.subList(from, Math.min(ids.size(), from + limit)));
        return new ContentSearchResult(toProjects(packs), offset, ids.size());
    }

    private int countMatching(List<Long> ids, String gameVersion) {
        int count = 0;
        for (Long id : ids) {
            Pack pack = details.get(id);
            if (pack != null && pack.supports(gameVersion)) {
                count++;
            }
        }
        return count;
    }

    private List<Long> allIds() throws IOException {
        List<Long> ids = allIds;
        if (ids == null) {
            IdList list = parse(PackDownloads.getText(API + "all"), IdList.class);
            ids = list == null || list.packs == null ? Collections.<Long>emptyList() : list.packs;
            allIds = ids;
        }
        return ids;
    }

    /**
     * Fetches the details of these packs, a few at a time, keeping the order. Packs the
     * API will not describe - withdrawn or private ones - are left out.
     */
    private List<Pack> load(List<Long> ids) throws IOException {
        Map<Long, Future<Pack>> pending = new LinkedHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(6, runnable -> {
            Thread thread = new Thread(runnable, "FTB lookup");
            thread.setDaemon(true);
            return thread;
        });
        try {
            for (Long id : ids) {
                if (!details.containsKey(id)) {
                    pending.put(id, pool.submit(() -> fetchPack(id)));
                }
            }
            for (Map.Entry<Long, Future<Pack>> entry : pending.entrySet()) {
                try {
                    Pack pack = entry.getValue().get();
                    if (pack != null) {
                        details.put(entry.getKey(), pack);
                    }
                } catch (ExecutionException e) {
                    log.warn("Could not load FTB pack {}: {}", entry.getKey(), e.getCause().toString());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        List<Pack> result = new ArrayList<>();
        for (Long id : ids) {
            Pack pack = details.get(id);
            if (pack != null) {
                result.add(pack);
            }
        }
        return result;
    }

    private Pack fetchPack(long id) throws IOException {
        Pack pack = parse(PackDownloads.getText(API + id), Pack.class);
        if (pack == null || !"success".equals(pack.status) || pack.versions == null || pack.versions.isEmpty()) {
            return null;
        }
        return pack;
    }

    private Pack pack(String projectId) throws IOException {
        long id = Long.parseLong(projectId);
        Pack pack = details.get(id);
        if (pack == null) {
            pack = fetchPack(id);
            if (pack == null) {
                throw new IOException("FTB does not list this pack any more");
            }
            details.put(id, pack);
        }
        return pack;
    }

    private static List<ContentProject> toProjects(List<Pack> packs) {
        List<ContentProject> projects = new ArrayList<>();
        for (Pack pack : packs) {
            List<String> tags = new ArrayList<>();
            if (pack.tags != null) {
                for (Tag tag : pack.tags) {
                    // the game version is a tag too, but it reads oddly among the genres
                    if (tag.name != null && !Character.isDigit(tag.name.charAt(0)) && tags.size() < 4) {
                        tags.add(tag.name);
                    }
                }
            }
            projects.add(new ContentProject(String.valueOf(pack.id), pack.name, pack.synopsis,
                    pack.authors == null || pack.authors.isEmpty() ? "" : pack.authors.get(0).name,
                    pack.installs, pack.icon(), tags, pack.pageUrl()));
        }
        return projects;
    }

    @Override
    public List<String> listGameVersions() {
        // FTB packs only exist for a handful of versions; a short fixed list is far more
        // useful here than every Minecraft release ever made
        return java.util.Arrays.asList("1.21.1", "1.20.1", "1.19.2", "1.18.2", "1.16.5", "1.12.2", "1.7.10");
    }

    @Override
    public List<PackVersion> listVersions(String projectId) throws IOException {
        List<PackVersion> versions = new ArrayList<>();
        List<Version> sorted = new ArrayList<>(pack(projectId).versions);
        sorted.sort((a, b) -> Long.compare(b.updated, a.updated));
        for (Version version : sorted) {
            if (version.isPrivate) {
                continue;
            }
            versions.add(new PackVersion(String.valueOf(version.id), version.name, version.target("game"),
                    "release".equalsIgnoreCase(version.type)));
        }
        return versions;
    }

    @Override
    public String getChangelogHtml(String projectId, PackVersion version) throws IOException {
        Changelog changelog = parse(PackDownloads.getText(
                CHANGELOG_API + projectId + "/" + version.getId() + "/changelog"), Changelog.class);
        return changelog == null || StringUtils.isBlank(changelog.content)
                ? null : ChangelogHtml.fromMarkdown(changelog.content);
    }

    @Override
    public PackPlan prepare(String projectId, PackVersion version, ModpackImporter.ProgressListener listener)
            throws IOException {
        if (listener != null) {
            listener.onStage(ModpackImporter.Stage.RESOLVING);
        }
        Pack pack = pack(projectId);
        VersionDetail detail = parse(PackDownloads.getText(API + projectId + "/" + version.getId()),
                VersionDetail.class);
        if (detail == null || detail.files == null) {
            throw new IOException("FTB sent no file list for this version");
        }

        String loaderName = null;
        String loaderVersion = null;
        String gameVersion = null;
        if (detail.targets != null) {
            for (Target target : detail.targets) {
                if ("game".equals(target.type)) {
                    gameVersion = target.version;
                } else if ("modloader".equals(target.type)) {
                    loaderName = target.name;
                    loaderVersion = target.version;
                }
            }
        }
        PackPlan plan = new PackPlan(pack.name, detail.name, gameVersion,
                loaderName == null ? null : ModLoader.detect(loaderName), loaderVersion);

        Map<Long, Long> fromCurseForge = new LinkedHashMap<>();
        for (FileEntry file : detail.files) {
            if (file.serveronly || file.optional) {
                continue;
            }
            String path = PackPaths.join(file.path, file.name);
            if (StringUtils.isNotEmpty(file.url)) {
                List<String> urls = new ArrayList<>();
                urls.add(file.url);
                if (file.mirrors != null) {
                    urls.addAll(file.mirrors);
                }
                plan.getDownloads().add(PackPlan.Download.file(urls, path,
                        StringUtils.isEmpty(file.sha1) ? null : "SHA-1", file.sha1, file.size));
            } else if (file.curseforge != null && StringUtils.isNumeric(file.curseforge.file)) {
                fromCurseForge.put(Long.parseLong(file.curseforge.file), Long.parseLong(file.curseforge.project));
            } else {
                log.warn("FTB lists {} without any way to download it", path);
            }
        }
        if (!fromCurseForge.isEmpty()) {
            String key = CurseForgeProvider.getApiKey();
            if (key == null) {
                throw new IOException("some files of this pack are hosted on CurseForge, which needs an API key");
            }
            PackFormats.addCurseForgeFiles(plan, key, fromCurseForge);
        }
        return plan;
    }

    private static <T> T parse(String json, Class<T> type) throws IOException {
        try {
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new IOException("could not read what FTB sent", e);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------ responses

    private static final class IdList {
        List<Long> packs;
    }

    private static final class Pack {
        String status;
        long id;
        String name;
        String slug;
        String synopsis;
        long installs;
        List<Art> art;
        List<Author> authors;
        List<Tag> tags;
        List<Version> versions;

        String icon() {
            if (art != null) {
                for (Art candidate : art) {
                    if ("square".equals(candidate.type) && candidate.url != null) {
                        return candidate.url;
                    }
                }
            }
            return null;
        }

        String pageUrl() {
            return "https://www.feed-the-beast.com/modpacks/" + id + (slug == null ? "" : "-" + slug);
        }

        boolean supports(String gameVersion) {
            for (Version version : versions) {
                if (gameVersion.equals(version.target("game"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class Art {
        String url;
        String type;
    }

    private static final class Author {
        String name;
    }

    private static final class Tag {
        String name;
    }

    private static final class Version {
        long id;
        String name;
        String type;
        long updated;
        @com.google.gson.annotations.SerializedName("private")
        boolean isPrivate;
        List<Target> targets;

        String target(String type) {
            if (targets != null) {
                for (Target target : targets) {
                    if (type.equals(target.type)) {
                        return target.version;
                    }
                }
            }
            return null;
        }
    }

    private static final class Target {
        String name;
        String version;
        String type;
    }

    private static final class VersionDetail {
        String name;
        List<Target> targets;
        List<FileEntry> files;
    }

    private static final class FileEntry {
        String path;
        String name;
        String url;
        List<String> mirrors;
        String sha1;
        long size;
        boolean serveronly;
        boolean optional;
        CurseForgeRef curseforge;
    }

    private static final class CurseForgeRef {
        String project;
        String file;
    }

    private static final class Changelog {
        String content;
    }
}
