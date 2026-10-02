package net.legacylauncher.modpack;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.modrinth.ContentProject;
import net.legacylauncher.modrinth.ContentSearchResult;
import net.legacylauncher.modrinth.ModLoader;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Packs from the Technic Platform.
 * <p>
 * A Technic pack comes one of two ways. Most are served by a "Solder" server, which lists
 * every build of the pack and, for each, a zip per mod to unpack into the game directory.
 * The rest are a single zip of the whole pack. Either way the loader itself sits in a
 * {@code bin/} folder the official launcher runs directly; this launcher installs loaders
 * from its own version list instead, so {@code bin/} is only read to tell which loader it
 * is, never copied.
 * <p>
 * Technic has no "browse everything" listing, only a search, so an empty search shows a
 * fixed set of the platform's best-known packs.
 */
@Slf4j
public final class TechnicModpackSource implements ModpackSource {
    public static final String ID = "technic";

    private static final String API = "https://api.technicpack.net/";
    /**
     * The Technic API wants to know which launcher build is asking; any recent number does.
     */
    private static final String BUILD = "build=999";
    private static final Gson GSON = new Gson();

    /**
     * Shown before anything is searched for - the platform's best-known packs, checked to
     * exist under these names.
     */
    private static final List<String> FEATURED = Arrays.asList(
            "tekkitmain", "hexxit", "attack-of-the-bteam", "tekkit-legends", "tekkit", "blightfall",
            "pixelmon-reforged", "tekkit-2", "the-1710-pack", "tekkitlite", "voltz", "bigdig");

    private static final List<String> SKIP_BIN = Collections.singletonList("bin/");

    private final Map<String, Pack> details = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Technic";
    }

    @Override
    public ContentSearchResult search(String query, String gameVersion, int offset, int limit) throws IOException {
        List<String> slugs;
        if (StringUtils.isBlank(query)) {
            slugs = FEATURED;
        } else {
            SearchResponse response = parse(PackDownloads.getText(
                    API + "search?" + BUILD + "&q=" + encode(query.trim())), SearchResponse.class);
            slugs = new ArrayList<>();
            if (response != null && response.modpacks != null) {
                for (SearchHit hit : response.modpacks) {
                    if (hit.slug != null) {
                        slugs.add(hit.slug);
                    }
                }
            }
        }
        int from = Math.min(offset, slugs.size());
        List<ContentProject> projects = new ArrayList<>();
        for (Pack pack : load(slugs.subList(from, Math.min(slugs.size(), from + limit)))) {
            projects.add(new ContentProject(pack.name, pack.displayName, firstLine(pack.description), pack.user,
                    pack.installs, pack.icon != null ? pack.icon.url : null, Collections.<String>emptyList(),
                    pack.platformUrl));
        }
        return new ContentSearchResult(projects, offset, slugs.size());
    }

    /**
     * Technic descriptions are often several paragraphs; a card has room for one.
     */
    private static String firstLine(String description) {
        if (description == null) {
            return "";
        }
        String text = description.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.length() > 220 ? text.substring(0, 217) + "..." : text;
    }

    private List<Pack> load(List<String> slugs) throws IOException {
        Map<String, Future<Pack>> pending = new LinkedHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(6, runnable -> {
            Thread thread = new Thread(runnable, "Technic lookup");
            thread.setDaemon(true);
            return thread;
        });
        try {
            for (String slug : slugs) {
                if (!details.containsKey(slug)) {
                    pending.put(slug, pool.submit(() -> fetchPack(slug)));
                }
            }
            for (Map.Entry<String, Future<Pack>> entry : pending.entrySet()) {
                try {
                    Pack pack = entry.getValue().get();
                    if (pack != null) {
                        details.put(entry.getKey(), pack);
                    }
                } catch (ExecutionException e) {
                    log.warn("Could not load Technic pack {}: {}", entry.getKey(), e.getCause().toString());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        List<Pack> result = new ArrayList<>();
        for (String slug : slugs) {
            Pack pack = details.get(slug);
            if (pack != null) {
                result.add(pack);
            }
        }
        return result;
    }

    private Pack fetchPack(String slug) throws IOException {
        Pack pack = parse(PackDownloads.getText(API + "modpack/" + encode(slug) + "?" + BUILD), Pack.class);
        return pack == null || pack.name == null ? null : pack;
    }

    private Pack pack(String slug) throws IOException {
        Pack pack = details.get(slug);
        if (pack == null) {
            pack = fetchPack(slug);
            if (pack == null) {
                throw new IOException("Technic does not list this pack any more");
            }
            details.put(slug, pack);
        }
        return pack;
    }

    @Override
    public List<String> listGameVersions() {
        // the platform's own Minecraft field is the version of whatever build was current
        // when the page was last edited, years ago for some packs - filtering on it would
        // hide packs that do have the version
        return Collections.emptyList();
    }

    @Override
    public List<PackVersion> listVersions(String projectId) throws IOException {
        Pack pack = pack(projectId);
        List<PackVersion> versions = new ArrayList<>();
        if (StringUtils.isNotEmpty(pack.solder)) {
            SolderPack solder = parse(PackDownloads.getText(solderBase(pack) + "modpack/" + encode(pack.name)),
                    SolderPack.class);
            if (solder != null && solder.builds != null) {
                List<String> builds = new ArrayList<>(solder.builds);
                Collections.reverse(builds);
                for (String build : builds) {
                    // "recommended" is the build the author vouches for; later ones are
                    // offered too, marked as not quite a release
                    boolean recommended = build.equals(solder.recommended);
                    versions.add(new PackVersion(build, build, null,
                            recommended || isBefore(builds, build, solder.recommended)));
                }
            }
        } else if (StringUtils.isNotEmpty(pack.url)) {
            versions.add(new PackVersion(pack.version == null ? "latest" : pack.version, pack.version,
                    pack.minecraft, true));
        }
        return versions;
    }

    /**
     * @return whether {@code build} is older than {@code recommended} in a newest-first list
     */
    private static boolean isBefore(List<String> newestFirst, String build, String recommended) {
        int buildIndex = newestFirst.indexOf(build);
        int recommendedIndex = newestFirst.indexOf(recommended);
        return recommendedIndex >= 0 && buildIndex > recommendedIndex;
    }

    @Override
    public String getChangelogHtml(String projectId, PackVersion version) throws IOException {
        // Technic keeps no changelog per build; the pack's news feed is what the platform
        // itself shows instead
        Pack pack = pack(projectId);
        if (pack.feed == null || pack.feed.isEmpty()) {
            return null;
        }
        StringBuilder html = new StringBuilder("<html><body><ul>");
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM);
        for (FeedEntry entry : pack.feed) {
            html.append("<li><b>").append(format.format(new Date(entry.date * 1000L))).append("</b> — ")
                    .append(escape(entry.content == null ? "" : entry.content));
            if (entry.url != null) {
                html.append(" <a href=\"").append(entry.url).append("\">↗</a>");
            }
            html.append("</li>");
        }
        return html.append("</ul></body></html>").toString();
    }

    @Override
    public PackPlan prepare(String projectId, PackVersion version, ModpackImporter.ProgressListener listener)
            throws IOException {
        Pack pack = pack(projectId);
        if (StringUtils.isNotEmpty(pack.solder)) {
            return prepareSolder(pack, version, listener);
        }
        if (StringUtils.isEmpty(pack.url)) {
            throw new IOException("Technic offers no download for this pack");
        }
        return prepareZip(pack, listener);
    }

    private PackPlan prepareSolder(Pack pack, PackVersion version, ModpackImporter.ProgressListener listener)
            throws IOException {
        if (listener != null) {
            listener.onStage(ModpackImporter.Stage.RESOLVING);
        }
        SolderBuild build = parse(PackDownloads.getText(
                solderBase(pack) + "modpack/" + encode(pack.name) + "/" + encode(version.getId())), SolderBuild.class);
        if (build == null || build.mods == null) {
            throw new IOException("Technic sent no file list for build " + version.getId());
        }

        ModLoader loader = null;
        String loaderVersion = null;
        for (SolderMod mod : build.mods) {
            ModLoader detected = loaderOf(mod.name);
            if (detected != null) {
                loader = detected;
                loaderVersion = mod.version;
                break;
            }
        }
        if (loader == null && StringUtils.isNotEmpty(build.forge)) {
            loader = ModLoader.FORGE;
            loaderVersion = build.forge;
        }

        PackPlan plan = new PackPlan(pack.displayName, version.getName(), build.minecraft, loader,
                trimGameVersion(loaderVersion, build.minecraft));
        for (SolderMod mod : build.mods) {
            if (StringUtils.isEmpty(mod.url)) {
                continue;
            }
            plan.getDownloads().add(PackPlan.Download.archive(mod.url, "",
                    StringUtils.isEmpty(mod.md5) ? null : "MD5", mod.md5, mod.filesize, SKIP_BIN));
        }
        return plan;
    }

    private PackPlan prepareZip(Pack pack, ModpackImporter.ProgressListener listener) throws IOException {
        if (listener != null) {
            listener.onStage(ModpackImporter.Stage.DOWNLOADING_PACK);
        }
        File zip = PackDownloads.fetchToTemp(pack.url, null, null, -1, listener);
        try {
            ModLoader loader = null;
            String loaderVersion = null;
            try (ZipFile file = new ZipFile(zip)) {
                ZipEntry versionJson = file.getEntry("bin/version.json");
                if (versionJson != null) {
                    try (Reader reader = new InputStreamReader(file.getInputStream(versionJson), StandardCharsets.UTF_8)) {
                        VersionJson json = GSON.fromJson(reader, VersionJson.class);
                        if (json != null && json.id != null) {
                            loader = ModLoader.detect(json.id);
                            loaderVersion = json.id;
                        }
                    }
                } else if (file.getEntry("bin/modpack.jar") != null) {
                    // the old way of shipping Forge: its jar, renamed
                    loader = ModLoader.FORGE;
                }
            }
            PackPlan plan = new PackPlan(pack.displayName, pack.version, pack.minecraft, loader,
                    loaderVersion == null ? null : lastVersionPart(loaderVersion));
            plan.getOverlays().add(new PackPlan.Overlay(zip, "", SKIP_BIN));
            plan.addScratch(zip);
            return plan;
        } catch (IOException | RuntimeException e) {
            zip.delete();
            throw e;
        }
    }

    private static ModLoader loaderOf(String modName) {
        if (modName == null) {
            return null;
        }
        String name = modName.toLowerCase(Locale.ROOT);
        switch (name) {
            case "forge":
            case "minecraftforge":
                return ModLoader.FORGE;
            case "neoforge":
                return ModLoader.NEOFORGE;
            case "fabric":
            case "fabric-loader":
            case "fabricloader":
                return ModLoader.FABRIC;
            case "quilt":
            case "quilt-loader":
                return ModLoader.QUILT;
            default:
                return null;
        }
    }

    /**
     * Solder writes the Forge version either bare or prefixed with the game version, such
     * as {@code 1.12.2-14.23.5.2860}.
     */
    private static String trimGameVersion(String loaderVersion, String gameVersion) {
        if (loaderVersion == null || gameVersion == null) {
            return loaderVersion;
        }
        return loaderVersion.startsWith(gameVersion + "-") ? loaderVersion.substring(gameVersion.length() + 1)
                : loaderVersion;
    }

    private static String lastVersionPart(String id) {
        int dash = id.lastIndexOf('-');
        return dash >= 0 ? id.substring(dash + 1) : id;
    }

    private static String solderBase(Pack pack) {
        return pack.solder.endsWith("/") ? pack.solder : pack.solder + "/";
    }

    private static <T> T parse(String json, Class<T> type) throws IOException {
        try {
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new IOException("could not read what Technic sent", e);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------ responses

    private static final class SearchResponse {
        List<SearchHit> modpacks;
    }

    private static final class SearchHit {
        String slug;
    }

    private static final class Pack {
        String name;
        String displayName;
        String user;
        String url;
        String platformUrl;
        String minecraft;
        String version;
        String description;
        String solder;
        long installs;
        Image icon;
        List<FeedEntry> feed;
    }

    private static final class Image {
        String url;
    }

    private static final class FeedEntry {
        long date;
        String content;
        String url;
    }

    private static final class SolderPack {
        String recommended;
        List<String> builds;
    }

    private static final class SolderBuild {
        String minecraft;
        String forge;
        List<SolderMod> mods;
    }

    private static final class SolderMod {
        String name;
        String version;
        String md5;
        String url;
        long filesize;
    }

    private static final class VersionJson {
        String id;
    }
}
