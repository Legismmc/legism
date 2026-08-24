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
        boolean enabled = LegacyLauncher.getInstance().getSettings().getBoolean(SETTING_ENABLED)
                && clientId != null && !clientId.trim().isEmpty();
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
