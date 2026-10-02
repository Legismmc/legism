package net.legacylauncher.modpack;

import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.modrinth.ContentSearchResult;

import java.io.IOException;
import java.util.List;

/**
 * A library whole modpacks can be installed from - Modrinth, CurseForge, FTB, Technic or
 * ATLauncher.
 * <p>
 * Kept apart from {@link net.legacylauncher.modrinth.ContentProvider}, which installs
 * single files into an existing instance: most of these libraries only carry modpacks, and
 * none of them installs a pack as "one file".
 * <p>
 * Every method blocks, so callers must stay off the Swing thread.
 */
public interface ModpackSource {
    /**
     * Stable identifier, stored in {@link ModpackOrigin#getSource()}.
     */
    String getId();

    String getDisplayName();

    default boolean isAvailable() {
        return true;
    }

    /**
     * @return a {@code ModrinthStrings} key saying why {@link #isAvailable()} is false
     */
    default String getUnavailableReason() {
        return null;
    }

    /**
     * @param query       what the user typed; may be empty, in which case the library's
     *                    own idea of popular or recent packs is shown
     * @param gameVersion only packs with a version for this Minecraft version, or
     *                    {@code null} for any
     */
    ContentSearchResult search(String query, String gameVersion, int offset, int limit) throws IOException;

    /**
     * @see net.legacylauncher.modrinth.ContentProvider#getMaxSearchDepth()
     */
    default int getMaxSearchDepth() {
        return Integer.MAX_VALUE;
    }

    /**
     * @return the Minecraft versions worth offering as a filter, newest first; empty when
     * this library cannot filter by version at all
     */
    List<String> listGameVersions() throws IOException;

    /**
     * @return every published version of one pack, newest first
     */
    List<PackVersion> listVersions(String projectId) throws IOException;

    /**
     * @return what changed in this version, as HTML, or {@code null} when the library
     * keeps no changelog
     */
    String getChangelogHtml(String projectId, PackVersion version) throws IOException;

    /**
     * Works out what installing this version takes. May download the pack's own archive
     * to read it, reporting that through the listener.
     */
    PackPlan prepare(String projectId, PackVersion version, ModpackImporter.ProgressListener listener)
            throws IOException;

    /**
     * The version to install when the user just clicks "install": the newest release for
     * the chosen Minecraft version, or the newest of anything when there is no release.
     *
     * @return {@code null} when nothing fits
     */
    default PackVersion pickVersion(String projectId, String gameVersion) throws IOException {
        PackVersion fallback = null;
        for (PackVersion version : listVersions(projectId)) {
            if (gameVersion != null && version.getGameVersion() != null
                    && !gameVersion.equals(version.getGameVersion())) {
                continue;
            }
            if (version.isRelease()) {
                return version;
            }
            if (fallback == null) {
                fallback = version;
            }
        }
        return fallback;
    }
}
