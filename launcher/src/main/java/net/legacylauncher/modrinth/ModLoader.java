package net.legacylauncher.modrinth;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * The mod loaders this launcher can install mods for. The {@link #getId() id} is the
 * value Modrinth uses in its {@code loaders} field and in search facets.
 */
public enum ModLoader {
    FABRIC("fabric", "Fabric"),
    FORGE("forge", "Forge"),
    NEOFORGE("neoforge", "NeoForge"),
    QUILT("quilt", "Quilt");

    private final String id;
    private final String displayName;

    ModLoader(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Recognises a loader by any string that may mention it - a version id, a family
     * name or a Modrinth loader id.
     *
     * @return the loader, or {@code null} when the string mentions none of them
     */
    public static ModLoader detect(String haystack) {
        if (StringUtils.isEmpty(haystack)) {
            return null;
        }
        String lower = haystack.toLowerCase(Locale.ROOT);
        // neoforge has to be tested before forge: it contains "forge" as a substring
        if (lower.contains("neoforge")) {
            return NEOFORGE;
        }
        if (lower.contains("quilt")) {
            return QUILT;
        }
        if (lower.contains("fabric")) {
            return FABRIC;
        }
        if (lower.contains("forge")) {
            return FORGE;
        }
        return null;
    }
}
