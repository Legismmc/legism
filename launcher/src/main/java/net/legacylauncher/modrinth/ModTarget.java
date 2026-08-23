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
 * One game directory that content can be installed into, plus what Modrinth has to be
 * asked for so the content actually loads there.
 * <p>
 * For a plain launcher version the directory is resolved exactly like the launcher's own
 * "open mods folder" menu entry does. For an instance it is the instance's own folder.
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
    private final File gameDir;

    private ModTarget(String versionId, String gameVersion, ModLoader loader, File gameDir) {
        this.versionId = versionId;
        this.gameVersion = gameVersion;
        this.loader = loader;
        this.gameDir = gameDir;
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

    /**
     * The game directory itself - the one Minecraft is started with.
     */
    public File getGameDir() {
        return gameDir;
    }

    /**
     * Where content of the given type belongs, e.g. {@code <gameDir>/resourcepacks}.
     */
    public File getDirectory(ContentType type) {
        return new File(gameDir, type.getFolder());
    }

    /**
     * Where the game keeps its singleplayer worlds.
     */
    public File getSavesDir() {
        return new File(gameDir, "saves");
    }

    public boolean supportsMods() {
        return loader != null;
    }

    public ModTarget withLoader(ModLoader newLoader) {
        return new ModTarget(versionId, gameVersion, newLoader, gameDir);
    }

    public ModTarget withGameVersion(String newGameVersion) {
        return new ModTarget(versionId, newGameVersion, loader, gameDir);
    }

    /**
     * Derives the target from a launcher version, using the shared game directory the
     * launcher would start it in.
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
        return of(version, rootDirOf(version, configuration));
    }

    /**
     * Derives the target from a launcher version installed into a directory of its own -
     * an instance.
     */
    public static ModTarget of(CompleteVersion version, File gameDir) {
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

        log.debug("Content target for {}: game version {}, loader {}, dir {}",
                id, gameVersion, loader, gameDir);

        return new ModTarget(id, gameVersion, loader, gameDir);
    }

    /**
     * Builds a target for a version id that is not installed yet, so an instance can be
     * edited before it has ever been launched.
     */
    public static ModTarget ofVersionId(String versionId, File gameDir) {
        ModLoader loader = ModLoader.detect(versionId);
        String gameVersion = extractGameVersion(versionId);
        return new ModTarget(versionId, gameVersion, loader, gameDir);
    }

    /**
     * Mirrors {@code FolderButton}: with separate game directories switched on the game
     * lives under {@code home/<family>} or {@code home/<version id>}, otherwise directly
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

    /**
     * Pulls the plain Minecraft version out of a launcher version id, e.g. {@code 1.20.1}
     * from {@code Forge 1.20.1}.
     *
     * @return {@code null} when the id names none
     */
    public static String extractGameVersion(String candidate) {
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
        return "ModTarget{" + versionId + ", game=" + gameVersion + ", loader=" + loader
                + ", dir=" + gameDir + "}";
    }
}
