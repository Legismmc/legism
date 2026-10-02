package net.legacylauncher.modpack;

import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.modrinth.ContentSearchResult;
import net.legacylauncher.modrinth.ContentType;
import net.legacylauncher.modrinth.ModrinthApi;
import net.legacylauncher.modrinth.ModrinthFile;
import net.legacylauncher.modrinth.ModrinthProvider;
import net.legacylauncher.modrinth.ModrinthVersion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modrinth's modpacks: each version is one {@code .mrpack}.
 */
public final class ModrinthModpackSource implements ModpackSource {
    public static final String ID = "modrinth";

    private final ModrinthProvider provider = new ModrinthProvider();

    /**
     * Versions seen in the last version listing, so showing a changelog for one does not
     * take another request - Modrinth sends the changelog along with the list.
     */
    private final Map<String, ModrinthVersion> seen = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Modrinth";
    }

    @Override
    public ContentSearchResult search(String query, String gameVersion, int offset, int limit) throws IOException {
        return provider.search(ContentType.MODPACK, query, gameVersion, null, null, offset, limit);
    }

    @Override
    public List<String> listGameVersions() throws IOException {
        return provider.listGameVersions();
    }

    @Override
    public List<PackVersion> listVersions(String projectId) throws IOException {
        List<PackVersion> versions = new ArrayList<>();
        for (ModrinthVersion version : ModrinthApi.listVersions(ContentType.MODPACK, projectId, null, null)) {
            seen.put(version.getId(), version);
            List<String> games = version.getGameVersions();
            versions.add(new PackVersion(version.getId(),
                    version.getVersionNumber() != null ? version.getVersionNumber() : version.getName(),
                    games == null || games.isEmpty() ? null : games.get(0),
                    "release".equals(version.getVersionType())));
        }
        return versions;
    }

    @Override
    public String getChangelogHtml(String projectId, PackVersion version) throws IOException {
        ModrinthVersion full = seen.get(version.getId());
        if (full == null) {
            full = ModrinthApi.getVersion(version.getId());
        }
        String changelog = full.getChangelog();
        return changelog == null || changelog.trim().isEmpty() ? null : ChangelogHtml.fromMarkdown(changelog);
    }

    @Override
    public PackPlan prepare(String projectId, PackVersion version, ModpackImporter.ProgressListener listener)
            throws IOException {
        ModrinthVersion full = ModrinthApi.getVersion(version.getId());
        ModrinthFile file = full.getPrimaryFile();
        if (file == null) {
            throw new IOException("this version of the modpack has no file to download");
        }
        if (listener != null) {
            listener.onStage(ModpackImporter.Stage.DOWNLOADING_PACK);
        }
        File pack = PackDownloads.fetchToTemp(file.getUrl(), "SHA-1", file.getSha1(), file.getSize(), listener);
        try {
            PackPlan plan = PackFormats.read(pack, listener);
            plan.addScratch(pack);
            return plan;
        } catch (IOException | RuntimeException e) {
            pack.delete();
            throw e;
        }
    }
}
