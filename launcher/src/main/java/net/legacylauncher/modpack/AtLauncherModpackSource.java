package net.legacylauncher.modpack;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.modrinth.ContentProject;
import net.legacylauncher.modrinth.ContentSearchResult;
import net.legacylauncher.modrinth.ModLoader;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ATLauncher's public packs.
 * <p>
 * ATLauncher publishes its whole catalogue - about a hundred and fifty packs - as one
 * listing, so it is fetched once and searched locally. Each pack version has a
 * {@code Configs.json} listing its mods, most hosted on ATLauncher's own CDN, plus a zip
 * of configuration files.
 * <p>
 * Mods marked "browser" have to be downloaded by hand, the same as CurseForge's
 * distribution-locked files, and are listed the same way. Optional mods are installed
 * when the pack recommends them, which is what ATLauncher itself ticks by default.
 */
@Slf4j
public final class AtLauncherModpackSource implements ModpackSource {
    public static final String ID = "atlauncher";

    private static final String API = "https://api.atlauncher.com/v1/";
    private static final String CDN = "https://download.nodecdn.net/containers/atl/";
    private static final String IMAGES = "https://cdn.atlcdn.net/images/packs/";
    private static final Gson GSON = new Gson();

    private volatile List<Pack> catalogue;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "ATLauncher";
    }

    private List<Pack> catalogue() throws IOException {
        List<Pack> packs = catalogue;
        if (packs == null) {
            PackList list = parse(PackDownloads.getText(API + "packs/full/public"), PackList.class);
            packs = new ArrayList<>();
            if (list != null && list.data != null) {
                for (Pack pack : list.data) {
                    if (pack.safeName != null && pack.versions != null && !pack.versions.isEmpty()) {
                        packs.add(pack);
                    }
                }
            }
            // no download counts are published, so the most recently updated packs -
            // the ones still maintained - come first
            packs.sort(Comparator.comparingLong(Pack::lastPublished).reversed());
            catalogue = packs;
        }
        return packs;
    }

    private Pack pack(String safeName) throws IOException {
        for (Pack pack : catalogue()) {
            if (pack.safeName.equals(safeName)) {
                return pack;
            }
        }
        throw new IOException("ATLauncher does not list this pack any more");
    }

    @Override
    public ContentSearchResult search(String query, String gameVersion, int offset, int limit) throws IOException {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Pack> matches = new ArrayList<>();
        for (Pack pack : catalogue()) {
            if (!needle.isEmpty() && !pack.name.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            if (gameVersion != null && !pack.supports(gameVersion)) {
                continue;
            }
            matches.add(pack);
        }
        int from = Math.min(offset, matches.size());
        List<ContentProject> projects = new ArrayList<>();
        for (Pack pack : matches.subList(from, Math.min(matches.size(), from + limit))) {
            List<String> tags = Collections.singletonList("Minecraft " + pack.versions.get(0).minecraft);
            projects.add(new ContentProject(pack.safeName, pack.name, pack.description, "", 0,
                    IMAGES + pack.safeName.toLowerCase(Locale.ROOT) + ".png", tags,
                    StringUtils.isNotEmpty(pack.websiteURL) ? pack.websiteURL
                            : "https://atlauncher.com/pack/" + pack.safeName));
        }
        return new ContentSearchResult(projects, offset, matches.size());
    }

    @Override
    public List<String> listGameVersions() throws IOException {
        Set<String> versions = new LinkedHashSet<>();
        List<Pack> packs = new ArrayList<>(catalogue());
        for (Pack pack : packs) {
            for (Version version : pack.versions) {
                if (version.minecraft != null) {
                    versions.add(version.minecraft);
                }
            }
        }
        List<String> sorted = new ArrayList<>(versions);
        sorted.sort(AtLauncherModpackSource::compareVersionsDescending);
        return sorted;
    }

    private static int compareVersionsDescending(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int left = i < x.length && StringUtils.isNumeric(x[i]) ? Integer.parseInt(x[i]) : 0;
            int right = i < y.length && StringUtils.isNumeric(y[i]) ? Integer.parseInt(y[i]) : 0;
            if (left != right) {
                return Integer.compare(right, left);
            }
        }
        return 0;
    }

    @Override
    public List<PackVersion> listVersions(String projectId) throws IOException {
        List<PackVersion> versions = new ArrayList<>();
        for (Version version : pack(projectId).versions) {
            versions.add(new PackVersion(version.version, version.version, version.minecraft, true));
        }
        return versions;
    }

    @Override
    public String getChangelogHtml(String projectId, PackVersion version) throws IOException {
        VersionInfo info = parse(PackDownloads.getText(API + "pack/" + projectId + "/" + version.getId()),
                VersionInfo.class);
        String text = info == null || info.data == null ? null : info.data.changelog;
        return StringUtils.isBlank(text) ? null : ChangelogHtml.fromPlainText(text);
    }

    @Override
    public PackPlan prepare(String projectId, PackVersion version, ModpackImporter.ProgressListener listener)
            throws IOException {
        if (listener != null) {
            listener.onStage(ModpackImporter.Stage.RESOLVING);
        }
        Pack pack = pack(projectId);
        String base = CDN + "packs/" + projectId + "/versions/" + version.getId() + "/";
        Configs configs = parse(PackDownloads.getText(base + "Configs.json"), Configs.class);
        if (configs == null) {
            throw new IOException("ATLauncher sent no file list for this version");
        }

        ModLoader loader = null;
        String loaderVersion = null;
        if (configs.loader != null) {
            loader = ModLoader.detect(configs.loader.type);
            loaderVersion = configs.loader.metadata == null ? null : configs.loader.metadata.version;
        }
        String gameVersion = StringUtils.isNotEmpty(configs.minecraft) ? configs.minecraft : version.getGameVersion();

        List<Mod> mods = configs.mods == null ? Collections.<Mod>emptyList() : configs.mods;
        if (loader == null) {
            // older packs list Forge among their mods instead of naming a loader
            for (Mod mod : mods) {
                ModLoader detected = mod.type == null ? null : ModLoader.detect(mod.type);
                if (detected != null) {
                    loader = detected;
                    loaderVersion = mod.version;
                    break;
                }
            }
        }

        PackPlan plan = new PackPlan(pack.name, version.getName(), gameVersion, loader, loaderVersion);
        for (Mod mod : mods) {
            if (!mod.client || (mod.optional && !mod.recommended) || mod.type == null) {
                continue;
            }
            String folder = folderFor(mod, gameVersion);
            if (folder == null) {
                continue; // the loader itself, or a jar mod - installed another way, if at all
            }
            String url = "server".equals(mod.download) ? CDN + mod.url : mod.url;
            String hashAlgorithm = StringUtils.isEmpty(mod.md5) ? null : "MD5";
            if ("browser".equals(mod.download) || StringUtils.isEmpty(url)) {
                plan.getSkipped().add(new PackPlan.Skipped(mod.name, mod.file, mod.url, null, folder));
            } else if ("extract".equals(mod.type)) {
                plan.getDownloads().add(PackPlan.Download.archive(url, folder, hashAlgorithm, mod.md5,
                        mod.filesize, null));
            } else {
                plan.getDownloads().add(PackPlan.Download.file(url, PackPaths.join(folder, mod.file),
                        hashAlgorithm, mod.md5, mod.filesize));
            }
        }
        if (!configs.noConfigs) {
            String sha1 = configs.configs == null ? null : configs.configs.sha1;
            long size = configs.configs == null ? -1 : configs.configs.filesize;
            plan.getDownloads().add(PackPlan.Download.archive(base + "Configs.zip", "",
                    StringUtils.isEmpty(sha1) ? null : "SHA-1", sha1, size, null));
        }
        return plan;
    }

    /**
     * @return where a mod of this type goes, or {@code null} for one this launcher installs
     * differently - the mod loader, which it takes from its own version list
     */
    private static String folderFor(Mod mod, String gameVersion) {
        switch (mod.type.toLowerCase(Locale.ROOT)) {
            case "mods":
                return "mods";
            case "resourcepack":
                return "resourcepacks";
            case "texturepack":
                return "texturepacks";
            case "shaderpack":
                return "shaderpacks";
            case "plugins":
                return "plugins";
            case "coremods":
                return "coremods";
            case "dependency":
                return gameVersion == null ? "mods" : "mods/" + gameVersion;
            case "ic2lib":
                return "mods/ic2";
            case "denlib":
                return "mods/denlib";
            case "flan":
                return "Flan";
            case "extract":
                String into = mod.extractTo == null ? "root" : mod.extractTo.toLowerCase(Locale.ROOT);
                return "root".equals(into) ? "" : into;
            default:
                return null;
        }
    }

    private static <T> T parse(String json, Class<T> type) throws IOException {
        try {
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new IOException("could not read what ATLauncher sent", e);
        }
    }

    // ------------------------------------------------------------ responses

    private static final class PackList {
        List<Pack> data;
    }

    private static final class Pack {
        String name;
        String safeName;
        String description;
        String websiteURL;
        List<Version> versions;

        long lastPublished() {
            return versions == null || versions.isEmpty() ? 0 : versions.get(0).published;
        }

        boolean supports(String gameVersion) {
            for (Version version : versions) {
                if (gameVersion.equals(version.minecraft)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class Version {
        String version;
        String minecraft;
        long published;
    }

    private static final class VersionInfo {
        VersionData data;
    }

    private static final class VersionData {
        String changelog;
    }

    private static final class Configs {
        String minecraft;
        boolean noConfigs;
        Loader loader;
        List<Mod> mods;
        ConfigsArchive configs;
    }

    private static final class Loader {
        String type;
        LoaderMetadata metadata;
    }

    private static final class LoaderMetadata {
        String version;
    }

    private static final class ConfigsArchive {
        long filesize;
        String sha1;
    }

    private static final class Mod {
        String name;
        String version;
        String url;
        String file;
        String download;
        String md5;
        long filesize;
        String type;
        String extractTo;
        boolean client = true;
        boolean optional;
        boolean recommended = true;
    }
}
