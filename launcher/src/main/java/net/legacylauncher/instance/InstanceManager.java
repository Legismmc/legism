package net.legacylauncher.instance;

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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps the instance folders under {@code <working dir>/instances} and their descriptors
 * in sync with what the UI shows.
 * <p>
 * The folder on disk is the source of truth: there is no central index that could drift
 * out of step with it, so an instance copied in by hand shows up on the next refresh.
 */
@Slf4j
public class InstanceManager {
    public static final String ROOT_FOLDER = "instances";

    /**
     * Environment variable a desktop shortcut sets to start one instance straight away.
     */
    public static final String ENV_INSTANCE = "LL_INSTANCE";

    private static final int MAX_ID_LENGTH = 48;

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private List<Instance> cache = Collections.emptyList();

    /**
     * Overrides where instances live. Left unset in the launcher, where the folder follows
     * the working directory; set by tests so they never touch a real installation.
     */
    private final File rootOverride;

    public InstanceManager() {
        this(null);
    }

    public InstanceManager(File rootOverride) {
        this.rootOverride = rootOverride;
    }

    public interface Listener {
        void onInstancesChanged(List<Instance> instances);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * The folder every instance lives in. Sits next to the shared game files rather than
     * inside them, so a stray instance never looks like a Minecraft world.
     */
    public File getRoot() {
        return rootOverride != null
                ? rootOverride
                : new File(MinecraftUtil.getWorkingDirectory(false), ROOT_FOLDER);
    }

    /**
     * The instances known at the last {@link #refresh()}.
     */
    public List<Instance> getInstances() {
        return cache;
    }

    /**
     * Re-reads the instances folder.
     */
    public synchronized List<Instance> refresh() {
        List<Instance> found = new ArrayList<>();
        File[] folders = getRoot().listFiles();
        if (folders != null) {
            for (File folder : folders) {
                if (!folder.isDirectory()) {
                    continue;
                }
                Instance instance = read(folder);
                if (instance != null) {
                    found.add(instance);
                }
            }
        }
        Collections.sort(found, new Comparator<Instance>() {
            @Override
            public int compare(Instance a, Instance b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        cache = Collections.unmodifiableList(found);
        log.debug("Found {} instance(s) in {}", found.size(), getRoot());
        fireChanged();
        return cache;
    }

    private Instance read(File folder) {
        File descriptor = new File(folder, Instance.DESCRIPTOR);
        if (!descriptor.isFile()) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(descriptor.toPath(), StandardCharsets.UTF_8)) {
            Instance instance = U.getGson().fromJson(reader, Instance.class);
            if (instance == null || StringUtils.isEmpty(instance.getId())) {
                log.warn("Ignoring unusable instance descriptor: {}", descriptor);
                return null;
            }
            instance.setFolder(folder);
            return instance;
        } catch (IOException | JsonSyntaxException e) {
            log.warn("Could not read instance descriptor {}", descriptor, e);
            return null;
        }
    }

    /**
     * Creates a new instance folder and its descriptor.
     *
     * @param name      what the user typed; may be anything printable
     * @param versionId launcher version id to start
     */
    public synchronized Instance create(String name, String versionId) throws IOException {
        if (StringUtils.isBlank(name)) {
            throw new IOException("instance name is empty");
        }
        if (StringUtils.isBlank(versionId)) {
            throw new IOException("no Minecraft version chosen");
        }

        String id = uniqueId(name);
        File folder = new File(getRoot(), id);
        if (folder.exists()) {
            throw new IOException("instance folder already exists: " + folder);
        }

        Instance instance = new Instance(id, name.trim(), versionId, folder);
        FileUtil.createFolder(instance.getGameDir());
        save(instance);
        log.info("Created instance {} in {}", instance, folder);
        refresh();
        return instance;
    }

    /**
     * Writes the descriptor back to disk.
     */
    public void save(Instance instance) throws IOException {
        FileUtil.createFolder(instance.getFolder());
        try (Writer writer = Files.newBufferedWriter(
                instance.getDescriptorFile().toPath(), StandardCharsets.UTF_8)) {
            U.getGson().toJson(instance, writer);
        }
    }

    /**
     * Renames an instance. Only the display name changes: the folder keeps its id, so
     * nothing that points at it breaks.
     */
    public synchronized void rename(Instance instance, String newName) throws IOException {
        if (StringUtils.isBlank(newName)) {
            throw new IOException("instance name is empty");
        }
        instance.setName(newName.trim());
        save(instance);
        refresh();
    }

    /**
     * Deletes the instance folder with everything in it - worlds included.
     */
    public synchronized void delete(Instance instance) throws IOException {
        File folder = instance.getFolder();
        log.info("Deleting instance {} ({})", instance, folder);
        FileUtil.deleteDirectory(folder);
        if (folder.exists()) {
            throw new IOException("could not delete " + folder);
        }
        refresh();
    }

    /**
     * Files the instance under a group, or under the default one when the name is blank.
     */
    public synchronized void setGroup(Instance instance, String group) throws IOException {
        instance.setGroup(group);
        save(instance);
        refresh();
    }

    /**
     * @return every group currently in use, sorted, without the default one
     */
    public List<String> getGroups() {
        Set<String> groups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Instance instance : cache) {
            if (!instance.getGroup().isEmpty()) {
                groups.add(instance.getGroup());
            }
        }
        return new ArrayList<>(groups);
    }

    /**
     * Copies an instance, game directory and all, under a new name.
     */
    public synchronized Instance duplicate(Instance source, String newName) throws IOException {
        Instance copy = create(newName, source.getVersionId());
        copy.setGroup(source.getGroup());
        save(copy);
        FileUtil.deleteDirectory(copy.getGameDir());
        copyDirectory(source.getGameDir().toPath(), copy.getGameDir().toPath());
        log.info("Duplicated {} into {}", source, copy);
        refresh();
        return copy;
    }

    private static void copyDirectory(final Path from, final Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            Files.createDirectories(to);
            return;
        }
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Writes the whole instance folder into a zip, so it can be handed to someone else.
     */
    public void export(Instance instance, File destination) throws IOException {
        final Path root = instance.getFolder().getAbsoluteFile().toPath().normalize();
        log.info("Exporting {} to {}", instance, destination);
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(destination.toPath()), StandardCharsets.UTF_8)) {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entry = root.relativize(file).toString().replace(File.separatorChar, '/');
                    zip.putNextEntry(new ZipEntry(entry));
                    Files.copy(file, zip);
                    zip.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    log.warn("Skipping unreadable file while exporting: {}", file, e);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    // ------------------------------------------------------------ play sessions

    private Instance running;
    private long sessionStarted;

    /**
     * Records that this instance is being launched. The elapsed time is added to its total
     * when {@link #finishSession()} is called.
     */
    public synchronized void startSession(Instance instance) {
        running = instance;
        sessionStarted = System.currentTimeMillis();
        instance.setLastPlayed(sessionStarted);
        try {
            save(instance);
        } catch (IOException e) {
            log.warn("Could not record the last played time of {}", instance, e);
        }
        fireChanged();
    }

    /**
     * Adds the elapsed time to the running instance's total, if any is running.
     */
    public synchronized void finishSession() {
        if (running == null) {
            return;
        }
        running.addPlayTime(System.currentTimeMillis() - sessionStarted);
        try {
            save(running);
        } catch (IOException e) {
            log.warn("Could not record the play time of {}", running, e);
        }
        log.debug("Play session of {} ended, total {} ms", running, running.getTotalPlayTime());
        running = null;
        fireChanged();
    }

    /**
     * The instance the game is currently running from, or {@code null}.
     */
    public synchronized Instance getRunning() {
        return running;
    }

    /**
     * Milliseconds played across every instance, for the status line.
     */
    public long getTotalPlayTime() {
        long total = 0L;
        for (Instance instance : cache) {
            total += instance.getTotalPlayTime();
        }
        return total;
    }

    private void fireChanged() {
        for (Listener listener : listeners) {
            try {
                listener.onInstancesChanged(cache);
            } catch (RuntimeException e) {
                log.warn("Instance listener failed", e);
            }
        }
    }

    /**
     * Turns a display name into a folder name that is safe on every platform, and makes
     * sure it is not already taken.
     */
    private String uniqueId(String name) {
        Set<String> taken = new LinkedHashSet<>();
        File[] folders = getRoot().listFiles();
        if (folders != null) {
            for (File folder : folders) {
                taken.add(folder.getName().toLowerCase(Locale.ROOT));
            }
        }

        String base = toId(name);
        if (!taken.contains(base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + "-" + i;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return base + "-" + System.currentTimeMillis();
    }

    static String toId(String name) {
        StringBuilder result = new StringBuilder(name.length());
        boolean lastWasDash = false;
        for (int i = 0; i < name.length() && result.length() < MAX_ID_LENGTH; i++) {
            char c = Character.toLowerCase(name.charAt(i));
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            if (ok) {
                result.append(c);
                lastWasDash = false;
            } else if (!lastWasDash && result.length() > 0) {
                result.append('-');
                lastWasDash = true;
            }
        }
        while (result.length() > 0 && result.charAt(result.length() - 1) == '-') {
            result.setLength(result.length() - 1);
        }
        // a name written entirely in a non-latin script leaves nothing usable behind
        return result.length() == 0 ? "instance" : result.toString();
    }
}
