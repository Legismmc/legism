package net.legacylauncher.modrinth;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A kind of downloadable content, mapping Modrinth's project types onto the folders
 * Minecraft actually reads them from.
 * <p>
 * Worlds are deliberately absent: Modrinth hosts no such project type, so saves are
 * managed locally instead.
 */
public enum ContentType {
    /**
     * Loader mods. The only type where the mod loader has to match.
     */
    MOD("mod", "mods", Collections.singletonList(".jar"), true),

    /**
     * Resource packs, which Minecraft reads from {@code resourcepacks} as zips.
     */
    RESOURCE_PACK("resourcepack", "resourcepacks", Arrays.asList(".zip", ".mcpack"), false),

    /**
     * Shader packs. Iris and OptiFine both read {@code shaderpacks}.
     */
    SHADER("shader", "shaderpacks", Collections.singletonList(".zip"), false),

    /**
     * Data packs. Minecraft loads these per world, so they are staged in a shared folder
     * and copied into a save by the user.
     */
    DATA_PACK("datapack", "datapacks", Collections.singletonList(".zip"), false),

    /**
     * Bukkit/Spigot/Paper-family server plugins, staged in {@code plugins}.
     */
    PLUGIN("plugin", "plugins", Collections.singletonList(".jar"), false),

    /**
     * CurseForge's "Addons" category - content packs that extend one particular mod
     * (gun packs for TACZ, hero packs for Fisk's Superheroes, and so on) rather than
     * standing on their own. Modrinth has no equivalent project type.
     * <p>
     * Each extending mod reads these from its own folder, so there is no single correct
     * destination the launcher could pick; they are staged in {@code addons} for the user
     * to move where the mod's own instructions say.
     */
    ADDON("addon", "addons", Collections.singletonList(".zip"), false),

    /**
     * A whole modpack. Unlike every other type this is never installed *into* a game
     * directory - a modpack becomes an instance of its own - so {@link #getFolder()} is
     * never consulted for it. It is a {@link ContentType} only so the libraries can be
     * searched for one.
     */
    MODPACK("modpack", "modpacks", Arrays.asList(".mrpack", ".zip"), false);

    private final String modrinthType;
    private final String folder;
    private final List<String> extensions;
    private final boolean loaderSpecific;

    ContentType(String modrinthType, String folder, List<String> extensions, boolean loaderSpecific) {
        this.modrinthType = modrinthType;
        this.folder = folder;
        this.extensions = Collections.unmodifiableList(extensions);
        this.loaderSpecific = loaderSpecific;
    }

    /**
     * The value Modrinth uses in its {@code project_type} facet.
     */
    public String getModrinthType() {
        return modrinthType;
    }

    /**
     * Folder inside the game directory, e.g. {@code mods}.
     */
    public String getFolder() {
        return folder;
    }

    /**
     * File extensions a download of this type is allowed to have.
     */
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * Whether searches for this type have to be narrowed to one mod loader. Only mods
     * do: a resource pack or a shader works regardless of what the instance runs.
     */
    public boolean isLoaderSpecific() {
        return loaderSpecific;
    }

    public boolean accepts(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Key under which {@code ModrinthStrings} holds this type's name.
     */
    public String getTitleKey() {
        return "type." + modrinthType;
    }
}
