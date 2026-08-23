package net.legacylauncher.stats;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.minecraft.Server;
import net.legacylauncher.minecraft.auth.Account;
import net.minecraft.launcher.versions.CompleteVersion;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Telemetry sink.
 * <p>
 * Upstream sent every launch, every account creation and every advertisement impression
 * to its own backend. This fork does not report anything anywhere: the methods are kept
 * so that call sites read the same, but they only write to the local debug log.
 */
@Slf4j
public final class Stats {

    public static void setAllowed(boolean allowed) {
        if (allowed) {
            log.debug("Remote statistics are permanently disabled in this build");
        }
    }

    public static void minecraftLaunched(Account<?> account, CompleteVersion version, Server server, int serverId) {
        log.debug("Minecraft launched: version={}, server={}", version.getID(), server);
    }

    public static Future<?> reportSessionDuration(long sessionStartTimeMillis) {
        return CompletableFuture.completedFuture(null);
    }

    public static void accountCreation(String type, String strategy, String step, boolean success) {
    }

    public static void feedbackStarted() {
    }

    public static void jarscannedCompleted(long seconds) {
        log.debug("Jar scan completed in {} s", seconds);
    }

    public static void jarscannedDetected(String name, String entry, String sha256) {
        log.warn("Malware signature detected in {} (entry {}, sha256 {})", name, entry, sha256);
    }

    public static void fractureiserTraceDetected() {
        log.warn("fractureiser trace detected");
    }

    private Stats() {
    }
}
