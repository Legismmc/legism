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
    private long created;
    private long lastPlayed;
    private long totalPlayTime;

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
