package net.legacylauncher.modrinth;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.configuration.Configuration;
import net.legacylauncher.util.MinecraftUtil;
import net.minecraft.launcher.updater.VersionSyncInfo;
import net.minecraft.launcher.versions.CompleteVersion;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where mods go for one installed Minecraft version, and what Modrinth has to be asked
 * for so the mods actually load there.
 * <p>
 * The mods directory is resolved exactly like the launcher's own "open mods folder"
 * menu entry does, so downloaded mods land where the game looks for them.
 */
@Slf4j
public final class ModTarget {
    /**
     * Matches a snapshot id such as 24w14a.
     */
    private static final Pattern SNAPSHOT = Pattern.compile("(\\d\\dw\\d\\d[a-z])");

    /**
     * Matches a Minecraft release - 1.20, 1.20.1, 1.7.10. Anchored on the leading "1."
     * on purpose: an id like {@code fabric-loader-0.15.7-1.20.4} names the loader version
     * first, and every Minecraft release since 1.0 starts with "1.".
     */
    private static final Pattern RELEASE = Pattern.compile("(?<![\\d.])(1\\.\\d+(?:\\.\\d+)?)(?![\\d.])");

    /**
     * Last resort for an id that names no 1.x version at all.
     */
    private static final Pattern ANY_VERSION = Pattern.compile("(?<![\\d.])(\\d+\\.\\d+(?:\\.\\d+)?)(?![\\d.])");

    private final String versionId;
    private final String gameVersion;
    private final ModLoader loader;
    private final File modsDir;

    private ModTarget(String versionId, String gameVersion, ModLoader loader, File modsDir) {
        this.versionId = versionId;
        this.gameVersion = gameVersion;
        this.loader = loader;
        this.modsDir = modsDir;
    }

    /**
     * The launcher version id this target was derived from, e.g. {@code 1.20.1-forge-47.2.0}.
     */
    public String getVersionId() {
        return versionId;
    }

    /**
     * The plain Minecraft version, e.g. {@code 1.20.1}; {@code null} when it could not
     * be worked out from the version id.
     */
    public String getGameVersion() {
        return gameVersion;
    }

    /**
     * {@code null} for a vanilla version, which cannot load mods.
     */
    public ModLoader getLoader() {
        return loader;
    }

    public File getModsDir() {
        return modsDir;
    }

    public boolean supportsMods() {
        return loader != null;
    }

    public ModTarget withLoader(ModLoader newLoader) {
        return new ModTarget(versionId, gameVersion, newLoader, modsDir);
    }

    public ModTarget withGameVersion(String newGameVersion) {
        return new ModTarget(versionId, newGameVersion, loader, modsDir);
    }

    /**
     * Derives the target from the version currently selected in the launcher.
     *
     * @return {@code null} if no version is selected or it is not installed yet
     */
    public static ModTarget of(VersionSyncInfo syncInfo, Configuration configuration) {
        if (syncInfo == null) {
            return null;
        }
        CompleteVersion version = syncInfo.getLocalCompleteVersion();
        if (version == null) {
            return null;
        }

        String id = version.getID();
        String family = version.getFamily();

        ModLoader loader = ModLoader.detect(id);
        if (loader == null) {
            loader = ModLoader.detect(family);
        }

        String gameVersion = extractGameVersion(version.getJar());
        if (gameVersion == null) {
            gameVersion = extractGameVersion(id);
        }
        if (gameVersion == null) {
            gameVersion = extractGameVersion(family);
        }

        File modsDir = new File(rootDirOf(version, configuration), "mods");

        log.debug("Mod target for {}: game version {}, loader {}, mods dir {}",
                id, gameVersion, loader, modsDir);

        return new ModTarget(id, gameVersion, loader, modsDir);
    }

    /**
     * Mirrors {@code FolderButton}: with separate game directories switched on the mods
     * live under {@code home/<family>} or {@code home/<version id>}, otherwise directly
     * in the working directory.
     */
    private static File rootDirOf(CompleteVersion version, Configuration configuration) {
        String dirName = null;
        Configuration.SeparateDirs separateDirs = configuration.getSeparateDirs();
        if (separateDirs != null) {
            switch (separateDirs) {
                case FAMILY:
                    dirName = version.getFamily();
                    break;
                case VERSION:
                    dirName = version.getID();
                    break;
                default:
                    break;
            }
        }
        if (StringUtils.isNotEmpty(dirName)) {
            return new File(MinecraftUtil.getWorkingDirectory(false), "home/" + dirName);
        }
        return MinecraftUtil.getWorkingDirectory(false);
    }

    static String extractGameVersion(String candidate) {
        if (StringUtils.isEmpty(candidate)) {
            return null;
        }
        Matcher snapshot = SNAPSHOT.matcher(candidate);
        if (snapshot.find()) {
            return snapshot.group(1);
        }
        Matcher release = RELEASE.matcher(candidate);
        if (release.find()) {
            return release.group(1);
        }
        Matcher any = ANY_VERSION.matcher(candidate);
        if (any.find()) {
            return any.group(1);
        }
        return null;
    }

    @Override
    public String toString() {
        return "ModTarget{" + versionId + ", game=" + gameVersion + ", loader=" + loader + "}";
    }
}
