package net.legacylauncher.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps exactly one {@link LocalServerProcess} per server, so starting a server, closing
 * its console and reopening it later reattaches to the same running process instead of
 * losing track of it - hosting a server is supposed to survive the UI being closed.
 */
public final class ServerProcessRegistry {
    private static final Map<String, LocalServerProcess> PROCESSES = new ConcurrentHashMap<>();

    private ServerProcessRegistry() {
    }

    public static LocalServerProcess get(ServerInstance server) {
        return PROCESSES.computeIfAbsent(server.getId(), id -> new LocalServerProcess(server));
    }
}
