package net.legacylauncher.server;

import java.io.File;

/**
 * A self-hosted Minecraft server: its own folder, its own core jar, its own
 * {@code server.properties} and plugins - independent of any client {@code Instance}.
 */
public class ServerInstance {
    /**
     * Name of the descriptor written into every server folder.
     */
    public static final String DESCRIPTOR = "server-instance.json";

    private String id;
    private String name;
    private ServerCore core;
    private String coreVersion;
    private String xmx = "2048";
    private int port = 25565;
    private long created;
    private long lastStarted;

    /**
     * Set after loading; not part of the descriptor.
     */
    private transient File folder;

    @SuppressWarnings("unused") // gson
    ServerInstance() {
    }

    ServerInstance(String id, String name, ServerCore core, String coreVersion, File folder) {
        this.id = id;
        this.name = name;
        this.core = core;
        this.coreVersion = coreVersion;
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

    public ServerCore getCore() {
        return core == null ? ServerCore.VANILLA : core;
    }

    /**
     * The Minecraft version this server's core jar was built for, e.g. {@code 1.20.1}.
     */
    public String getCoreVersion() {
        return coreVersion;
    }

    /**
     * Max heap size passed to the JVM, in MiB.
     */
    public String getXmx() {
        return xmx;
    }

    public void setXmx(String xmx) {
        this.xmx = xmx;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getCreated() {
        return created;
    }

    public long getLastStarted() {
        return lastStarted;
    }

    public void setLastStarted(long lastStarted) {
        this.lastStarted = lastStarted;
    }

    public File getFolder() {
        return folder;
    }

    void setFolder(File folder) {
        this.folder = folder;
    }

    public File getJarFile() {
        return new File(folder, "server.jar");
    }

    public File getPropertiesFile() {
        return new File(folder, "server.properties");
    }

    public File getEulaFile() {
        return new File(folder, "eula.txt");
    }

    @Override
    public String toString() {
        return getName();
    }
}
