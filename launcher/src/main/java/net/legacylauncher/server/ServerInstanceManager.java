package net.legacylauncher.server;

import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.FileUtil;
import net.legacylauncher.util.MinecraftUtil;
import net.legacylauncher.util.U;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Keeps the local server folders under {@code <working dir>/servers} and their descriptors
 * in sync with what the UI shows - the folder-scan-is-source-of-truth pattern
 * {@link net.legacylauncher.instance.InstanceManager} already uses for client instances.
 */
@Slf4j
public class ServerInstanceManager {
    public static final String ROOT_FOLDER = "servers";

    private List<ServerInstance> cache = Collections.emptyList();

    public File getRoot() {
        return new File(MinecraftUtil.getWorkingDirectory(false), ROOT_FOLDER);
    }

    public List<ServerInstance> getServers() {
        return cache;
    }

    public synchronized List<ServerInstance> refresh() {
        List<ServerInstance> found = new ArrayList<>();
        File[] folders = getRoot().listFiles();
        if (folders != null) {
            for (File folder : folders) {
                if (!folder.isDirectory()) {
                    continue;
                }
                ServerInstance server = read(folder);
                if (server != null) {
                    found.add(server);
                }
            }
        }
        found.sort(Comparator.comparing(ServerInstance::getName, String.CASE_INSENSITIVE_ORDER));
        cache = Collections.unmodifiableList(found);
        log.debug("Found {} local server(s) in {}", found.size(), getRoot());
        return cache;
    }

    private ServerInstance read(File folder) {
        File descriptor = new File(folder, ServerInstance.DESCRIPTOR);
        if (!descriptor.isFile()) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(descriptor.toPath(), StandardCharsets.UTF_8)) {
            ServerInstance server = U.getGson().fromJson(reader, ServerInstance.class);
            if (server == null || StringUtils.isEmpty(server.getId())) {
                log.warn("Ignoring unusable server descriptor: {}", descriptor);
                return null;
            }
            server.setFolder(folder);
            return server;
        } catch (IOException | JsonSyntaxException e) {
            log.warn("Could not read server descriptor {}", descriptor, e);
            return null;
        }
    }

    public synchronized ServerInstance create(String name, ServerCore core, String coreVersion) throws IOException {
        if (StringUtils.isBlank(name)) {
            throw new IOException("server name is empty");
        }
        String id = UUID.randomUUID().toString();
        File folder = new File(getRoot(), id);
        ServerInstance server = new ServerInstance(id, name.trim(), core, coreVersion, folder);
        FileUtil.createFolder(folder);
        save(server);
        log.info("Created local server {} ({}) in {}", server, core, folder);
        refresh();
        return server;
    }

    public void save(ServerInstance server) throws IOException {
        FileUtil.createFolder(server.getFolder());
        try (Writer writer = Files.newBufferedWriter(
                new File(server.getFolder(), ServerInstance.DESCRIPTOR).toPath(), StandardCharsets.UTF_8)) {
            U.getGson().toJson(server, writer);
        }
    }

    public synchronized void delete(ServerInstance server) throws IOException {
        File folder = server.getFolder();
        log.info("Deleting local server {} ({})", server, folder);
        FileUtil.deleteDirectory(folder);
        if (folder.exists()) {
            throw new IOException("could not delete " + folder);
        }
        refresh();
    }
}
