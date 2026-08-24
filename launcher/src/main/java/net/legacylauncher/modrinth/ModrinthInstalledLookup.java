package net.legacylauncher.modrinth;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Figures out what installed mods, resource packs and shaders actually are on Modrinth, by
 * hashing them and asking Modrinth to identify the hash - the same trick the official app
 * and other launchers use, since a file on disk carries no metadata of its own about where
 * it came from.
 * <p>
 * Every method blocks, so callers must stay off the Swing thread.
 */
@Slf4j
public final class ModrinthInstalledLookup {
    private ModrinthInstalledLookup() {
    }

    /**
     * @param mods        files to identify
     * @param type        what kind of content they are, for picking a compatible update
     * @param gameVersion the instance's Minecraft version, for the update check
     * @param loader      the instance's mod loader, ignored for non loader-specific content
     * @return one entry per file that Modrinth recognised; a file it does not know about is
     * simply absent from the result
     */
    public static Map<File, ModrinthMatch> identify(List<InstalledMod> mods, ContentType type,
                                                     String gameVersion, String loader) {
        Map<File, String> hashesByFile = new HashMap<>();
        List<String> hashes = new ArrayList<>();
        for (InstalledMod mod : mods) {
            String hash = ModInstaller.sha1(mod.getFile());
            if (hash != null) {
                hashesByFile.put(mod.getFile(), hash);
                hashes.add(hash);
            }
        }
        if (hashes.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        final Map<String, ModrinthVersion> versionsByHash;
        try {
            versionsByHash = ModrinthApi.getVersionFiles(hashes);
        } catch (ModrinthException e) {
            log.debug("Could not identify installed files: {}", e.toString());
            return java.util.Collections.emptyMap();
        }
        if (versionsByHash.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        Set<String> projectIds = new LinkedHashSet<>();
        for (ModrinthVersion version : versionsByHash.values()) {
            if (version.getProjectId() != null) {
                projectIds.add(version.getProjectId());
            }
        }

        final Map<String, ModrinthProject> projectsById = new HashMap<>();
        try {
            for (ModrinthProject project : ModrinthApi.getProjects(new ArrayList<>(projectIds))) {
                projectsById.put(project.getProjectId(), project);
            }
        } catch (ModrinthException e) {
            log.debug("Could not load project details for installed files: {}", e.toString());
        }

        Map<String, ModrinthVersion> latestByProject = fetchLatestVersions(projectIds, type, gameVersion, loader);

        Map<File, ModrinthMatch> result = new HashMap<>();
        for (Map.Entry<File, String> entry : hashesByFile.entrySet()) {
            ModrinthVersion installedVersion = versionsByHash.get(entry.getValue());
            if (installedVersion == null || installedVersion.getProjectId() == null) {
                continue;
            }
            ModrinthProject project = projectsById.get(installedVersion.getProjectId());
            ModrinthVersion latest = latestByProject.get(installedVersion.getProjectId());
            ContentFile latestFile = toContentFile(latest);
            result.put(entry.getKey(), new ModrinthMatch(
                    installedVersion.getProjectId(),
                    project == null ? installedVersion.getProjectId() : project.getTitle(),
                    project == null ? null : project.getIconUrl(),
                    installedVersion.getId(),
                    latest,
                    latestFile
            ));
        }
        return result;
    }

    /**
     * One {@code /project/{id}/version} request per distinct project, run in parallel - a
     * modpack-sized install list can easily be thirty projects, and doing that sequentially
     * would make opening the Installed tab feel frozen.
     */
    private static Map<String, ModrinthVersion> fetchLatestVersions(Set<String> projectIds, ContentType type,
                                                                     String gameVersion, String loader) {
        Map<String, ModrinthVersion> result = new HashMap<>();
        if (projectIds.isEmpty()) {
            return result;
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, projectIds.size()));
        try {
            Map<String, Future<ModrinthVersion>> futures = new HashMap<>();
            for (String projectId : projectIds) {
                futures.put(projectId, pool.submit(() -> {
                    try {
                        return ModrinthProvider.pickBest(
                                ModrinthApi.listVersions(type, projectId, gameVersion, loader));
                    } catch (IOException e) {
                        log.debug("Could not check {} for updates: {}", projectId, e.toString());
                        return null;
                    }
                }));
            }
            for (Map.Entry<String, Future<ModrinthVersion>> entry : futures.entrySet()) {
                try {
                    ModrinthVersion latest = entry.getValue().get(20, TimeUnit.SECONDS);
                    if (latest != null) {
                        result.put(entry.getKey(), latest);
                    }
                } catch (Exception e) {
                    log.debug("Update check for {} did not finish: {}", entry.getKey(), e.toString());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return result;
    }

    private static ContentFile toContentFile(ModrinthVersion version) {
        if (version == null) {
            return null;
        }
        ModrinthFile file = version.getPrimaryFile();
        if (file == null) {
            return null;
        }
        return new ContentFile(file.getFilename(), file.getUrl(), file.getSize(),
                file.getSha512(), file.getSha1(), null);
    }
}
