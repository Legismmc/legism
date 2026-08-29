package net.legacylauncher.modrinth;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CurseForge, available once an API key has been entered in the launcher settings.
 * <p>
 * CurseForge requires every third-party application to register for its own key, so there
 * is none to ship. It also lets mod authors forbid third-party downloads, and withholds
 * the download link for those - such a file is reported rather than silently skipped.
 */
@Slf4j
public class CurseForgeProvider implements ContentProvider {
    public static final String ID = "curseforge";

    /**
     * Launcher setting holding the user's key.
     */
    public static final String API_KEY_SETTING = "curseforge.apikey";

    private static final int MAX_DEPENDENCY_DEPTH = 5;

    /**
     * The relation CurseForge uses for "this is required".
     */
    private static final int RELATION_REQUIRED = 3;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "CurseForge";
    }

    public static String getApiKey() {
        LegacyLauncher launcher = LegacyLauncher.getInstance();
        if (launcher == null) {
            return null;
        }
        String key = launcher.getSettings().get(API_KEY_SETTING);
        return StringUtils.isBlank(key) ? null : key.trim();
    }

    @Override
    public boolean isAvailable() {
        return getApiKey() != null;
    }

    @Override
    public String getUnavailableReason() {
        return isAvailable() ? null : "curseforge.no-key";
    }

    @Override
    public boolean supports(ContentType type) {
        // CurseForge files data packs under a class of their own (6945), so they are
        // browsable here after all - this used to claim otherwise.
        return true;
    }

    @Override
    public ContentSearchResult search(ContentType type, String query, String gameVersion,
                                      String loader, String sort, int offset, int limit) throws IOException {
        CurseForgeApi.SearchResponse response = CurseForgeApi.search(
                requireKey(), type, query, gameVersion, ModLoader.detect(loader),
                sortFieldOf(sort), offset, limit);

        List<ContentProject> hits = new ArrayList<>();
        for (CurseForgeApi.Mod mod : response.mods()) {
            hits.add(new ContentProject(
                    String.valueOf(mod.id),
                    mod.name,
                    mod.summary,
                    mod.authors == null || mod.authors.isEmpty() ? "" : mod.authors.get(0).name,
                    (long) mod.downloadCount,
                    mod.logo == null ? null : (mod.logo.thumbnailUrl != null ? mod.logo.thumbnailUrl : mod.logo.url),
                    categoriesOf(mod),
                    mod.links == null ? null : mod.links.websiteUrl
            ));
        }
        int total = response.pagination == null ? hits.size() : response.pagination.totalCount;
        return new ContentSearchResult(hits, offset, total);
    }

    private static List<String> categoriesOf(CurseForgeApi.Mod mod) {
        List<String> names = new ArrayList<>();
        if (mod.categories != null) {
            for (CurseForgeApi.Category category : mod.categories) {
                if (category.name != null) {
                    names.add(category.name);
                }
            }
        }
        return names;
    }

    @Override
    public List<ContentFile> plan(ContentType type, String projectId, String gameVersion,
                                  String loader, boolean withDependencies) throws IOException {
        String key = requireKey();
        ModLoader modLoader = ModLoader.detect(loader);

        CurseForgeApi.ModFile best = pickBest(
                CurseForgeApi.listFiles(key, type, projectId, gameVersion, modLoader).files());
        if (best == null) {
            return new ArrayList<>();
        }

        List<CurseForgeApi.ModFile> plan = new ArrayList<>();
        plan.add(best);
        if (withDependencies) {
            collectDependencies(key, type, best, gameVersion, modLoader, plan, new HashSet<>(), 0);
        }

        List<ContentFile> files = new ArrayList<>();
        for (CurseForgeApi.ModFile file : plan) {
            if (StringUtils.isEmpty(file.downloadUrl)) {
                // the author opted out of third-party downloads; saying so beats a file
                // that silently never appears
                throw new ModrinthException("CurseForge withholds the download link for "
                        + file.fileName + ": its author has opted out of third-party downloads");
            }
            files.add(new ContentFile(file.fileName, file.downloadUrl, file.fileLength,
                    null, file.hash(1), file.hash(2)));
        }
        return files;
    }

    private void collectDependencies(String key, ContentType type, CurseForgeApi.ModFile file,
                                     String gameVersion, ModLoader loader,
                                     List<CurseForgeApi.ModFile> plan, Set<Long> visited, int depth) {
        if (depth >= MAX_DEPENDENCY_DEPTH || file.dependencies == null) {
            return;
        }
        for (CurseForgeApi.Dependency dependency : file.dependencies) {
            if (dependency.relationType != RELATION_REQUIRED || !visited.add(dependency.modId)) {
                continue;
            }
            try {
                CurseForgeApi.ModFile resolved = pickBest(CurseForgeApi.listFiles(
                        key, type, String.valueOf(dependency.modId), gameVersion, loader).files());
                if (resolved == null) {
                    log.warn("Could not resolve required CurseForge dependency {}", dependency.modId);
                    continue;
                }
                plan.add(resolved);
                collectDependencies(key, type, resolved, gameVersion, loader, plan, visited, depth + 1);
            } catch (IOException e) {
                log.warn("Could not resolve required CurseForge dependency {}: {}",
                        dependency.modId, e.toString());
            }
        }
    }

    /**
     * Prefers a release over a beta or an alpha; CurseForge lists newest first.
     */
    private static CurseForgeApi.ModFile pickBest(List<CurseForgeApi.ModFile> files) {
        CurseForgeApi.ModFile fallback = null;
        for (CurseForgeApi.ModFile file : files) {
            if (file.releaseType == 1) {
                return file;
            }
            if (fallback == null) {
                fallback = file;
            }
        }
        return fallback;
    }

    private String requireKey() throws IOException {
        String key = getApiKey();
        if (key == null) {
            throw new ModrinthException("no CurseForge API key is set");
        }
        return key;
    }

    /**
     * CurseForge numbers its sort fields; these are the ones worth offering.
     */
    private static int sortFieldOf(String sort) {
        if ("downloads".equals(sort)) {
            return 6;
        }
        if ("newest".equals(sort)) {
            return 11;
        }
        if ("updated".equals(sort)) {
            return 3;
        }
        if ("follows".equals(sort)) {
            return 12;
        }
        return 1; // featured, the closest thing to relevance
    }

    /**
     * Measured against the live API: {@code index=9980} with a page of 20 is answered,
     * {@code index=9990} is a 400. The search also reports a totalCount of exactly 10000
     * however many results there really are, so this is the whole of what it will show.
     */
    @Override
    public int getMaxSearchDepth() {
        return 10000;
    }

    @Override
    public List<SortOption> getSortOptions() {
        return Arrays.asList(
                new SortOption("relevance", "sort.relevance"),
                new SortOption("downloads", "sort.downloads"),
                new SortOption("updated", "sort.updated"),
                new SortOption("newest", "sort.newest")
        );
    }

    @Override
    public List<String> listGameVersions() throws IOException {
        List<String> versions = new ArrayList<>();
        for (CurseForgeApi.VersionType type : CurseForgeApi.listGameVersions(requireKey()).types()) {
            if (type.versions != null) {
                versions.addAll(type.versions);
            }
        }
        return versions;
    }
}
