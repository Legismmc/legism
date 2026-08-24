package net.legacylauncher.minecraft.ping;

/**
 * What a server answered to a status ping.
 */
public final class ServerStatus {
    private final String motd;
    private final int onlinePlayers;
    private final int maxPlayers;
    private final String versionName;
    private final long latencyMs;

    public ServerStatus(String motd, int onlinePlayers, int maxPlayers, String versionName, long latencyMs) {
        this.motd = motd;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
        this.versionName = versionName;
        this.latencyMs = latencyMs;
    }

    public String getMotd() {
        return motd;
    }

    public int getOnlinePlayers() {
        return onlinePlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public String getVersionName() {
        return versionName;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}
