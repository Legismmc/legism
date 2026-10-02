package net.legacylauncher.modpack;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.instance.InstanceManager;
import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.instance.ModpackImporter.CancelledException;
import net.legacylauncher.instance.ModpackImporter.ProgressListener;
import net.legacylauncher.instance.ModpackImporter.Stage;
import net.legacylauncher.modrinth.ContentType;
import net.legacylauncher.modrinth.ModLoader;
import net.legacylauncher.modrinth.ModTarget;
import net.legacylauncher.ui.images.IconLoader;
import net.legacylauncher.util.FileUtil;
import net.legacylauncher.util.U;
import net.minecraft.launcher.updater.VersionSyncInfo;
import org.apache.commons.lang3.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Carries out a {@link PackPlan}: as a new instance, or as an update of an instance that
 * was installed from the same pack before.
 * <p>
 * Downloads run several at a time. An FTB pack is several thousand files, most of them a
 * few kilobytes of config, and fetching them one after another spent far longer waiting
 * on round trips than actually downloading.
 */
@Slf4j
public final class PackInstaller {
    /**
     * Parallel downloads. Enough to hide the round trips on a pack of small files, few
     * enough not to look like abuse to the CDNs serving them.
     */
    private static final int PARALLEL = 10;

    /**
     * Written into the instance folder: every path the pack put into the game directory,
     * so an update knows what it may remove and what the player added themselves.
     */
    public static final String TRACKING_FILE = "modpack-files.json";

    /**
     * The game directory an update is staged in before anything in the real one changes.
     */
    private static final String STAGING_FOLDER = ".pack-update";

    private static final int ICON_SIZE = 128;

    private PackInstaller() {
    }

    // ---------------------------------------------------------------- new instance

    /**
     * Installs the plan as a brand-new instance. On failure nothing of it is left behind.
     *
     * @param origin where the pack came from, kept with the instance for updates; or
     *               {@code null} for a pack imported from a file
     */
    public static ModpackImporter.Result installNew(PackPlan plan, InstanceManager manager, ModpackOrigin origin,
                                                    ProgressListener listener) throws IOException {
        try {
            String versionId = resolveVersionId(plan);
            String name = origin != null && StringUtils.isNotEmpty(origin.getName()) ? origin.getName() : plan.getName();
            Instance instance = manager.create(name, versionId);
            try {
                Set<String> installed = execute(plan, instance.getGameDir(), listener);
                writeTracking(instance, installed);
                if (origin != null) {
                    instance.setModpack(origin);
                    manager.save(instance);
                    applyIcon(manager, instance, origin.getIconUrl());
                }
            } catch (IOException | RuntimeException e) {
                // a half-installed modpack is worse than none - the user can just try again
                deleteIfPresent(instance.getFolder());
                manager.refresh();
                throw e;
            }
            manager.refresh();
            return new ModpackImporter.Result(instance, skippedFiles(plan, instance.getGameDir()));
        } finally {
            plan.cleanup();
        }
    }

    // ---------------------------------------------------------------- update

    /**
     * Switches an instance installed from a pack over to another version of it.
     * <p>
     * The new version is downloaded in full into a staging folder first, so a failed or
     * cancelled update leaves the instance exactly as it was. Only then are the old pack's
     * files that the new one no longer has removed, and the new ones moved in. Anything the
     * player added - their own mods, worlds, screenshots - is not the pack's and is left
     * alone, and so are their game settings and server list when the pack ships its own.
     *
     * @param origin the pack and version the instance now comes from
     */
    public static ModpackImporter.Result update(Instance instance, PackPlan plan, ModpackOrigin origin,
                                                InstanceManager manager, ProgressListener listener) throws IOException {
        String versionId;
        try {
            versionId = resolveVersionId(plan);
        } catch (IOException | RuntimeException e) {
            plan.cleanup();
            throw e;
        }
        return update(instance, plan, origin, versionId, manager, listener);
    }

    /**
     * {@link #update(Instance, PackPlan, ModpackOrigin, InstanceManager, ProgressListener)}
     * with the launcher version already picked.
     */
    static ModpackImporter.Result update(Instance instance, PackPlan plan, ModpackOrigin origin, String versionId,
                                         InstanceManager manager, ProgressListener listener) throws IOException {
        File staging = new File(instance.getFolder(), STAGING_FOLDER);
        try {
            deleteIfPresent(staging);
            Set<String> fresh = execute(plan, staging, listener);

            stage(listener, Stage.EXTRACTING);
            File gameDir = instance.getGameDir();
            Set<String> previous = readTracking(instance);
            for (String path : previous) {
                if (!fresh.contains(path) && !isPlayerOwned(path)) {
                    File old = PackPaths.resolve(gameDir, path);
                    if (old.isFile() && !old.delete()) {
                        log.warn("Could not remove {} left over from the previous pack version", old);
                    }
                }
            }
            for (String path : fresh) {
                File destination = PackPaths.resolve(gameDir, path);
                if (isPlayerOwned(path) && destination.exists()) {
                    continue;
                }
                FileUtil.createFolder(destination.getParentFile());
                Files.move(PackPaths.resolve(staging, path).toPath(), destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            writeTracking(instance, fresh);
            instance.setVersionId(versionId);
            instance.setModpack(origin);
            manager.save(instance);
            manager.refresh();
            return new ModpackImporter.Result(instance, skippedFiles(plan, gameDir));
        } finally {
            deleteIfPresent(staging);
            plan.cleanup();
        }
    }

    /**
     * Files a pack may ship but the player makes their own: their worlds, their game
     * settings, their server list. An update neither removes nor overwrites these.
     */
    private static boolean isPlayerOwned(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.startsWith("saves/")
                || lower.equals("options.txt")
                || lower.equals("optionsof.txt")
                || lower.equals("optionsshaders.txt")
                || lower.equals("servers.dat");
    }

    private static Set<String> readTracking(Instance instance) {
        File file = new File(instance.getFolder(), TRACKING_FILE);
        if (file.isFile()) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                List<String> paths = U.getGson().fromJson(reader, new TypeToken<List<String>>() {
                }.getType());
                if (paths != null) {
                    return new LinkedHashSet<>(paths);
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Could not read {}", file, e);
            }
        }
        // Installed before the launcher kept track. The mods folder is where a mismatch
        // actually breaks the game - two versions of one mod crash on start - so that is
        // what gets replaced; configs and the rest are simply overwritten.
        Set<String> guessed = new LinkedHashSet<>();
        File mods = new File(instance.getGameDir(), ContentType.MOD.getFolder());
        File[] files = mods.listFiles();
        if (files != null) {
            for (File file1 : files) {
                if (file1.isFile()) {
                    guessed.add(ContentType.MOD.getFolder() + "/" + file1.getName());
                }
            }
        }
        return guessed;
    }

    private static void writeTracking(Instance instance, Set<String> paths) throws IOException {
        File file = new File(instance.getFolder(), TRACKING_FILE);
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            U.getGson().toJson(new ArrayList<>(paths), writer);
        }
    }

    // ---------------------------------------------------------------- carrying out a plan

    /**
     * Puts everything the plan lists into {@code root}.
     *
     * @return every path written, relative to {@code root}
     */
    static Set<String> execute(PackPlan plan, File root, ProgressListener listener) throws IOException {
        FileUtil.createFolder(root);
        Set<String> written = Collections.synchronizedSet(new LinkedHashSet<>());
        File scratch = Files.createTempDirectory("legism-pack-").toFile();
        try {
            List<PackPlan.Download> downloads = plan.getDownloads();
            List<File> archives = downloadAll(downloads, root, scratch, written, listener);

            stage(listener, Stage.EXTRACTING);
            for (int i = 0; i < downloads.size(); i++) {
                PackPlan.Download download = downloads.get(i);
                if (download.isExtract()) {
                    checkCancelled(listener);
                    extract(archives.get(i), "", download.getPath(), download.getSkipPrefixes(), root, written);
                }
            }
            for (PackPlan.Overlay overlay : plan.getOverlays()) {
                checkCancelled(listener);
                extract(overlay.getZip(), overlay.getPrefix(), "", overlay.getSkipPrefixes(), root, written);
            }
        } finally {
            deleteIfPresent(scratch);
        }
        return new LinkedHashSet<>(written);
    }

    /**
     * @return for each download that is an archive, the scratch file it was saved to
     * (at the same index); {@code null} elsewhere
     */
    private static List<File> downloadAll(List<PackPlan.Download> downloads, File root, File scratch,
                                          Set<String> written, ProgressListener listener) throws IOException {
        List<File> archives = new ArrayList<>(Collections.<File>nCopies(downloads.size(), null));
        if (downloads.isEmpty()) {
            return archives;
        }
        stage(listener, Stage.DOWNLOADING_FILES);

        long knownTotal = 0;
        for (PackPlan.Download download : downloads) {
            knownTotal += Math.max(0, download.getSize());
        }
        final long totalBytes = knownTotal;
        final AtomicLong doneBytes = new AtomicLong();
        final AtomicInteger finished = new AtomicInteger();
        final AtomicBoolean failed = new AtomicBoolean();
        final int count = downloads.size();

        if (listener != null) {
            listener.onStep("", 0, count);
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(PARALLEL, count), runnable -> {
            Thread thread = new Thread(runnable, "Modpack download");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int index = i;
                final PackPlan.Download download = downloads.get(i);
                futures.add(pool.submit(() -> {
                    File destination;
                    if (download.isExtract()) {
                        destination = new File(scratch, index + ".zip");
                        archives.set(index, destination);
                    } else {
                        destination = PackPaths.resolve(root, download.getPath());
                    }
                    PackDownloads.fetch(download.getUrls(), destination, download.getHashAlgorithm(),
                            download.getHash(), download.getSize(),
                            new AggregateListener(listener, failed, doneBytes, totalBytes));
                    if (!download.isExtract()) {
                        written.add(download.getPath());
                    }
                    int done = finished.incrementAndGet();
                    if (listener != null) {
                        listener.onStep(download.getDisplayName(), done, count);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    failed.set(true);
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException) {
                        throw (IOException) cause;
                    }
                    throw new IOException(cause);
                } catch (InterruptedException e) {
                    failed.set(true);
                    Thread.currentThread().interrupt();
                    throw new CancelledException();
                }
            }
        } finally {
            failed.set(true);
            pool.shutdownNow();
        }
        return archives;
    }

    /**
     * Turns each download's own byte count into one figure for the whole pack, and stops
     * every other download once one has failed.
     */
    private static final class AggregateListener implements ProgressListener {
        private final ProgressListener delegate;
        private final AtomicBoolean failed;
        private final AtomicLong doneBytes;
        private final long totalBytes;
        private long reported;

        AggregateListener(ProgressListener delegate, AtomicBoolean failed, AtomicLong doneBytes, long totalBytes) {
            this.delegate = delegate;
            this.failed = failed;
            this.doneBytes = doneBytes;
            this.totalBytes = totalBytes;
        }

        @Override
        public void onStep(String message, int current, int total) {
        }

        @Override
        public void onBytes(long done, long total) {
            // a retry starts the file over, which makes this delta negative - as it should
            long delta = done - reported;
            reported = done;
            long sum = doneBytes.addAndGet(delta);
            if (delegate != null) {
                delegate.onBytes(sum, totalBytes);
            }
        }

        @Override
        public boolean isCancelled() {
            return failed.get() || (delegate != null && delegate.isCancelled());
        }
    }

    private static void extract(File zipFile, String prefix, String into, List<String> skip, File root,
                                Set<String> written) throws IOException {
        if (zipFile == null || !zipFile.isFile()) {
            return;
        }
        String base = PackPaths.normalize(into);
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !name.startsWith(prefix)) {
                    continue;
                }
                String relative = PackPaths.normalize(name.substring(prefix.length()));
                if (relative.isEmpty() || PackPaths.startsWithAny(relative, skip)) {
                    continue;
                }
                String path = PackPaths.join(base, relative);
                File destination = PackPaths.resolve(root, path);
                FileUtil.createFolder(destination.getParentFile());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                written.add(path);
            }
        }
    }

    private static List<ModpackImporter.SkippedFile> skippedFiles(PackPlan plan, File gameDir) {
        List<ModpackImporter.SkippedFile> result = new ArrayList<>();
        for (PackPlan.Skipped skipped : plan.getSkipped()) {
            File folder = skipped.getFolder().isEmpty() ? gameDir : new File(gameDir, skipped.getFolder());
            result.add(new ModpackImporter.SkippedFile(skipped.getName(), skipped.getFileName(),
                    skipped.getPageUrl(), skipped.getSha1(), folder));
        }
        return result;
    }

    // ---------------------------------------------------------------- version and icon

    /**
     * Picks the launcher version id providing this pack's Minecraft version and loader -
     * the exact loader version the pack asks for when the launcher offers it, otherwise the
     * newest one it has.
     */
    public static String resolveVersionId(PackPlan plan) throws IOException {
        String gameVersion = plan.getGameVersion();
        if (StringUtils.isEmpty(gameVersion)) {
            throw new IOException("modpack does not declare a Minecraft version");
        }
        ModLoader loader = plan.getLoader();
        String wanted = plan.getLoaderVersion();
        String first = null;
        for (VersionSyncInfo info : LegacyLauncher.getInstance().getVersionManager().getVersions(false)) {
            String id = info.getID();
            if (!gameVersion.equals(ModTarget.extractGameVersion(id)) || ModLoader.detect(id) != loader) {
                continue;
            }
            if (loader == null || StringUtils.isEmpty(wanted) || id.contains(wanted)) {
                return id;
            }
            if (first == null) {
                first = id;
            }
        }
        if (first != null) {
            log.info("{} {} is not offered; using {} instead", loader, wanted, first);
            return first;
        }
        throw new IOException("no installable version found for Minecraft " + gameVersion
                + (loader != null ? " (" + loader.getDisplayName() + ")" : ""));
    }

    /**
     * Gives the new instance the pack's own picture. Best effort: a pack without one, or
     * with one that cannot be read, just keeps the default icon.
     */
    private static void applyIcon(InstanceManager manager, Instance instance, String iconUrl) {
        if (StringUtils.isEmpty(iconUrl)) {
            return;
        }
        File raw = null;
        File png = null;
        try {
            raw = Files.createTempFile("legism-icon-", ".img").toFile();
            PackDownloads.fetch(iconUrl, raw, null, null, -1, null);
            BufferedImage image = IconLoader.decode(Files.readAllBytes(raw.toPath()), iconUrl, ICON_SIZE);
            if (image == null) {
                return;
            }
            png = Files.createTempFile("legism-icon-", ".png").toFile();
            ImageIO.write(scaleDown(image), "png", png);
            manager.setCustomIcon(instance, png);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not set the pack icon from {}: {}", iconUrl, e.toString());
        } finally {
            if (raw != null) {
                raw.delete();
            }
            if (png != null) {
                png.delete();
            }
        }
    }

    /**
     * Pack art is often a 1024px banner-quality image; the instance list never shows it
     * bigger than this, so there is no point keeping the rest.
     */
    private static BufferedImage scaleDown(BufferedImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        if (side <= ICON_SIZE) {
            return image;
        }
        BufferedImage square = image.getSubimage(
                (image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side);
        BufferedImage scaled = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(square, 0, 0, ICON_SIZE, ICON_SIZE, null);
        g.dispose();
        return scaled;
    }

    /**
     * The launcher's own helper refuses a folder that is not there, which is the normal
     * case for a staging folder before the first update.
     */
    private static void deleteIfPresent(File folder) {
        if (folder != null && folder.isDirectory()) {
            FileUtil.deleteDirectory(folder);
        }
    }

    private static void stage(ProgressListener listener, Stage stage) {
        if (listener != null) {
            listener.onStage(stage);
        }
    }

    private static void checkCancelled(ProgressListener listener) throws CancelledException {
        PackDownloads.checkCancelled(listener);
    }
}
