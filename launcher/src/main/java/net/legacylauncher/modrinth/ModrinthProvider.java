package net.legacylauncher.modrinth;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Modrinth, the library this launcher uses by default. Needs no account and no key.
 */
@Slf4j
public class ModrinthProvider implements ContentProvider {
    public static final String ID = "modrinth";

    /**
     * How deep required dependencies are followed. Modrinth's chains are shallow in
     * practice; the limit is there so a cycle in the metadata cannot turn into an endless
     * download.
     */
    private static final int MAX_DEPENDENCY_DEPTH = 5;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Modrinth";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getUnavailableReason() {
        return null;
    }

    @Override
    public boolean supports(ContentType type) {
        // "Addons" is a CurseForge category; Modrinth publishes no such project type
        return type != ContentType.ADDON;
    }

    @Override
    public ContentSearchResult search(ContentType type, String query, String gameVersion,
                                      String loader, String sort, int offset, int limit) throws IOException {
        ModrinthSearchResult result = ModrinthApi.search(type, query, gameVersion, loader, sort, offset, limit);
        List<ContentProject> hits = new ArrayList<>();
        for (ModrinthProject project : result.getHits()) {
            hits.add(new ContentProject(
                    project.getProjectId(),
                    project.getTitle(),
                    project.getDescription(),
                    project.getAuthor(),
                    project.getDownloads(),
                    project.getIconUrl(),
                    project.getDisplayCategories(),
                    project.getPageUrl()
            ));
        }
        return new ContentSearchResult(hits, result.getOffset(), result.getTotalHits());
    }

    @Override
    public List<ContentFile> plan(ContentType type, String projectId, String gameVersion,
                                  String loader, boolean withDependencies) throws IOException {
        List<ModrinthVersion> candidates = ModrinthApi.listVersions(type, projectId, gameVersion, loader);
        ModrinthVersion best = pickBest(candidates);
        if (best == null) {
            return new ArrayList<>();
        }

        List<ModrinthVersion> plan = new ArrayList<>();
        plan.add(best);
        if (withDependencies) {
            collectDependencies(type, best, gameVersion, loader, plan, new HashSet<>(), 0);
        }

        List<ContentFile> files = new ArrayList<>();
        for (ModrinthVersion version : plan) {
            ModrinthFile file = version.getPrimaryFile();
            if (file == null) {
                log.warn("Version {} carries no downloadable file, skipping", version);
                continue;
            }
            files.add(new ContentFile(file.getFilename(), file.getUrl(), file.getSize(),
                    file.getSha512(), file.getSha1(), null));
        }
        return files;
    }

    private void collectDependencies(ContentType type, ModrinthVersion version, String gameVersion,
                                     String loader, List<ModrinthVersion> plan,
                                     Set<String> visitedProjects, int depth) {
        if (depth >= MAX_DEPENDENCY_DEPTH) {
            log.warn("Stopping dependency resolution at depth {}", depth);
            return;
        }
        for (ModrinthDependency dependency : version.getDependencies()) {
            if (!dependency.isRequired()) {
                continue;
            }
            try {
                ModrinthVersion resolved = resolve(type, dependency, gameVersion, loader);
                if (resolved == null) {
                    log.warn("Could not resolve required dependency {} of {}", dependency, version);
                    continue;
                }
                if (resolved.getProjectId() != null && !visitedProjects.add(resolved.getProjectId())) {
                    continue;
                }
                if (containsSameFile(plan, resolved)) {
                    continue;
                }
                plan.add(resolved);
                collectDependencies(type, resolved, gameVersion, loader, plan, visitedProjects, depth + 1);
            } catch (IOException e) {
                log.warn("Could not resolve required dependency {}: {}", dependency, e.toString());
            }
        }
    }

    private ModrinthVersion resolve(ContentType type, ModrinthDependency dependency,
                                    String gameVersion, String loader) throws IOException {
        if (StringUtils.isNotEmpty(dependency.getVersionId())) {
            return ModrinthApi.getVersion(dependency.getVersionId());
        }
        if (StringUtils.isEmpty(dependency.getProjectId())) {
            return null;
        }
        return pickBest(ModrinthApi.listVersions(type, dependency.getProjectId(), gameVersion, loader));
    }

    private static boolean containsSameFile(List<ModrinthVersion> plan, ModrinthVersion candidate) {
        ModrinthFile candidateFile = candidate.getPrimaryFile();
        if (candidateFile == null) {
            return true; // nothing to install anyway
        }
        for (ModrinthVersion planned : plan) {
            ModrinthFile file = planned.getPrimaryFile();
            if (file != null && file.getFilename() != null
                    && file.getFilename().equals(candidateFile.getFilename())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Picks the newest version that fits, preferring a release over a beta or an alpha
     * when both are offered. Modrinth returns versions newest first.
     */
    static ModrinthVersion pickBest(List<ModrinthVersion> versions) {
        ModrinthVersion fallback = null;
        for (ModrinthVersion version : versions) {
            if (version.getPrimaryFile() == null) {
                continue;
            }
            if ("release".equals(version.getVersionType())) {
                return version;
            }
            if (fallback == null) {
                fallback = version;
            }
        }
        return fallback;
    }

    /**
     * No wall worth modelling: an offset of 20000 is answered just as happily as 0, and
     * the search reports the real total rather than a capped one.
     */
    @Override
    public int getMaxSearchDepth() {
        return Integer.MAX_VALUE;
    }

    @Override
    public List<SortOption> getSortOptions() {
        return Arrays.asList(
                new SortOption("relevance", "sort.relevance"),
                new SortOption("downloads", "sort.downloads"),
                new SortOption("follows", "sort.follows"),
                new SortOption("newest", "sort.newest"),
                new SortOption("updated", "sort.updated")
        );
    }

    @Override
    public List<String> listGameVersions() throws IOException {
        return ModrinthApi.listReleaseGameVersions();
    }
}
