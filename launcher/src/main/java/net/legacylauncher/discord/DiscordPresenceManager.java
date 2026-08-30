package net.legacylauncher.discord;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.instance.InstanceManager;
import net.legacylauncher.util.async.AsyncThread;

import java.util.List;

/**
 * Keeps Discord's Rich Presence in sync with whichever instance is currently running.
 * Connecting to Discord (or trying to) only happens when an instance actually starts, not
 * at launcher startup, since most sessions never touch this at all.
 */
@Slf4j
public final class DiscordPresenceManager implements InstanceManager.Listener {
    public static final String SETTING_ENABLED = "discord.rpc.enabled";
    public static final String SETTING_CLIENT_ID = "discord.rpc.client-id";

    /**
     * The fork's own Discord application, used when the user has not supplied one.
     * <p>
     * Encoded rather than written out, and kept here rather than in the settings defaults,
     * so it is neither greppable in the source nor copied into every user's tl.properties.
     * That is all this achieves: an application id is handed to Discord by every client
     * that connects and can be read out of a running launcher in seconds, so this is not
     * secrecy and nothing should be built on it being one.
     */
    private static final String BUNDLED_CLIENT_ID = "MTU0MzU5NjkyNDI5OTUwOTgzMA==";

    private static String bundledClientId() {
        try {
            return new String(java.util.Base64.getDecoder().decode(BUNDLED_CLIENT_ID),
                    java.nio.charset.StandardCharsets.US_ASCII);
        } catch (RuntimeException e) {
            log.debug("Could not read the bundled Discord id: {}", e.toString());
            return "";
        }
    }

    private DiscordRpc rpc;
    private Instance lastRunning;

    public void install() {
        LegacyLauncher.getInstance().getInstanceManager().addListener(this);
    }

    @Override
    public void onInstancesChanged(List<Instance> instances) {
        Instance running = LegacyLauncher.getInstance().getInstanceManager().getRunning();
        if (running == lastRunning) {
            return;
        }
        lastRunning = running;
        AsyncThread.execute(() -> update(running));
    }

    private void update(Instance running) {
        String clientId = LegacyLauncher.getInstance().getSettings().get(SETTING_CLIENT_ID);
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = bundledClientId();
        }
        boolean enabled = LegacyLauncher.getInstance().getSettings().getBoolean(SETTING_ENABLED)
                && !clientId.trim().isEmpty();
        if (!enabled) {
            return;
        }
        if (running == null) {
            if (rpc != null) {
                rpc.clearActivity();
            }
            return;
        }
        if (rpc == null) {
            rpc = new DiscordRpc(clientId);
        }
        if (rpc.connect()) {
            rpc.setActivity(running.getName(), System.currentTimeMillis());
        } else {
            log.debug("Discord not reachable, skipping Rich Presence for {}", running);
        }
    }
}
