package net.legacylauncher.instance;

import java.io.File;

/**
 * A self-contained Minecraft installation: its own game directory, its own version, its
 * own mods, resource packs, shaders and worlds.
 * <p>
 * Everything the launcher needs to start it lives in the instance folder, so instances
 * can be copied around and never interfere with one another - which is the whole point
 * of having them.
 */
public class Instance {
    /**
     * Name of the descriptor written into every instance folder.
     */
    public static final String DESCRIPTOR = "instance.json";

    /**
     * The game directory inside the instance folder. Kept separate from the descriptor so
     * launcher metadata never ends up among the player's saves.
     */
    public static final String GAME_SUBFOLDER = ".minecraft";

    private String id;
    private String name;
    private String versionId;
    private String group;
    private String icon;
    private String customIcon;
    private long created;
    private long lastPlayed;
    private long totalPlayTime;
    private String xmx;

    /**
     * Set after loading; not part of the descriptor.
     */
    private transient File folder;

    @SuppressWarnings("unused") // gson
    Instance() {
    }

    Instance(String id, String name, String versionId, File folder) {
        this.id = id;
        this.name = name;
        this.versionId = versionId;
        this.folder = folder;
        this.created = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name == null ? id : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Launcher version id, e.g. {@code 1.20.1} or {@code 1.20.1-forge-47.2.20}.
     */
    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public long getCreated() {
        return created;
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    /**
     * Group this instance is filed under in the list; blank means the default group.
     */
    public String getGroup() {
        return group == null ? "" : group;
    }

    public void setGroup(String group) {
        this.group = group == null || group.trim().isEmpty() ? null : group.trim();
    }

    /**
     * Id of the built-in icon picked for this instance, or {@code null} when none was ever
     * chosen - the UI then picks one deterministically from {@link #getId()}, so the
     * descriptor does not need migrating just to get an icon that varies between instances.
     */
    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    /**
     * File name of a user-uploaded icon image kept inside the instance folder, or
     * {@code null} when the instance uses one of the built-in icons instead. Takes priority
     * over {@link #getIcon()} whenever it is set.
     */
    public String getCustomIcon() {
        return customIcon;
    }

    public void setCustomIcon(String customIcon) {
        this.customIcon = customIcon;
    }

    /**
     * The custom icon file this instance points at, resolved against its own folder;
     * {@code null} when it has none.
     */
    public File getCustomIconFile() {
        return customIcon == null ? null : new File(folder, customIcon);
    }

    /**
     * Milliseconds spent in game across every session.
     */
    public long getTotalPlayTime() {
        return totalPlayTime;
    }

    public void addPlayTime(long millis) {
        if (millis > 0L) {
            this.totalPlayTime += millis;
        }
    }

    /**
     * How much memory to give this instance's JVM - {@code "auto"}, a number of MiB, or
     * {@code null} to use whatever the launcher's own memory setting resolves to. Memory
     * used to be one setting for every instance; now each one keeps its own.
     */
    public String getXmx() {
        return xmx;
    }

    public void setXmx(String xmx) {
        this.xmx = xmx == null || xmx.trim().isEmpty() ? null : xmx.trim();
    }

    /**
     * The instance folder, holding the descriptor and the game directory.
     */
    public File getFolder() {
        return folder;
    }

    void setFolder(File folder) {
        this.folder = folder;
    }

    /**
     * The directory Minecraft is started with - what the game sees as {@code .minecraft}.
     */
    public File getGameDir() {
        return new File(folder, GAME_SUBFOLDER);
    }

    public File getDescriptorFile() {
        return new File(folder, DESCRIPTOR);
    }

    @Override
    public String toString() {
        return "Instance{" + id + " \"" + getName() + "\" " + versionId + "}";
    }
}
