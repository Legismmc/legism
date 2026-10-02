package net.legacylauncher.modpack;

import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.modrinth.ContentSearchResult;
import net.legacylauncher.modrinth.ContentType;
import net.legacylauncher.modrinth.CurseForgeApi;
import net.legacylauncher.modrinth.CurseForgeProvider;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CurseForge's modpacks: each version is one zip with a {@code manifest.json}.
 */
public final class CurseForgeModpackSource implements ModpackSource {
    public static final String ID = "curseforge";

    private final CurseForgeProvider provider = new CurseForgeProvider();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "CurseForge";
    }

    @Override
    public boolean isAvailable() {
        return provider.isAvailable();
    }

    @Override
    public String getUnavailableReason() {
        return provider.getUnavailableReason();
    }

    @Override
    public int getMaxSearchDepth() {
        return provider.getMaxSearchDepth();
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
        for (CurseForgeApi.ModFile file : CurseForgeApi.listFiles(key(), ContentType.MODPACK, projectId, null, null)
                .files()) {
            versions.add(new PackVersion(String.valueOf(file.id),
                    StringUtils.isNotEmpty(file.displayName) ? file.displayName : file.fileName,
                    file.gameVersion(), file.releaseType == 1));
        }
        return versions;
    }

    @Override
    public String getChangelogHtml(String projectId, PackVersion version) throws IOException {
        String html = CurseForgeApi.getChangelog(key(), Long.parseLong(projectId), Long.parseLong(version.getId()));
        return StringUtils.isBlank(html) ? null : ChangelogHtml.fromHtml(html);
    }

    @Override
    public PackPlan prepare(String projectId, PackVersion version, ModpackImporter.ProgressListener listener)
            throws IOException {
        CurseForgeApi.ModFile file = CurseForgeApi.getFile(key(), Long.parseLong(projectId),
                Long.parseLong(version.getId())).data;
        if (file == null) {
            throw new IOException("CurseForge does not know this version of the modpack");
        }
        if (StringUtils.isEmpty(file.downloadUrl)) {
            throw new IOException("the author of this modpack only allows downloading it through "
                    + "CurseForge's own app");
        }
        if (listener != null) {
            listener.onStage(ModpackImporter.Stage.DOWNLOADING_PACK);
        }
        String sha1 = file.hash(1);
        File pack = PackDownloads.fetchToTemp(file.downloadUrl, sha1 == null ? null : "SHA-1", sha1,
                file.fileLength, listener);
        try {
            PackPlan plan = PackFormats.read(pack, listener);
            plan.addScratch(pack);
            return plan;
        } catch (IOException | RuntimeException e) {
            pack.delete();
            throw e;
        }
    }

    private static String key() throws IOException {
        String key = CurseForgeProvider.getApiKey();
        if (key == null) {
            throw new IOException("no CurseForge API key is set");
        }
        return key;
    }
}
