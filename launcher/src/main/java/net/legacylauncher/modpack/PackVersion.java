package net.legacylauncher.modpack;

/**
 * One published version of a modpack, as offered in the "update to" list.
 */
public final class PackVersion {
    private final String id;
    private final String name;
    private final String gameVersion;
    private final boolean release;

    /**
     * @param id          the library's own id for it - only that library can make sense of it
     * @param name        what to show
     * @param gameVersion the Minecraft version, or {@code null} when the library does not say
     * @param release     {@code false} for betas and alphas
     */
    public PackVersion(String id, String name, String gameVersion, boolean release) {
        this.id = id;
        this.name = name;
        this.gameVersion = gameVersion;
        this.release = release;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name == null ? id : name;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public boolean isRelease() {
        return release;
    }

    @Override
    public String toString() {
        return getName() + (gameVersion == null ? "" : "  —  " + gameVersion);
    }
}
