package net.legacylauncher.update;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.OS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Fetches the right build of a new release and hands it over to be installed.
 * <p>
 * Deliberately stops short of replacing the running launcher itself. On Windows the
 * installer is perfectly capable of upgrading an existing install, so this downloads it and
 * runs it; everywhere else - and for the portable builds - unpacking over a copy of
 * yourself while it is running is the kind of thing that half-works and leaves people with
 * a launcher that will not start, so the file is downloaded and its folder opened instead.
 * <p>
 * Either way the part worth automating is the same one: five files are published per
 * release across three operating systems, and picking the right one by hand is the step
 * people actually get wrong.
 */
@Slf4j
public final class SelfUpdater {

    /**
     * Left behind by the Inno Setup installer, and the only dependable sign that this copy
     * was installed rather than unpacked from the portable archive - both layouts are
     * otherwise identical, right down to the bundled runtime.
     */
    private static final String UNINSTALLER = "unins000.exe";

    private SelfUpdater() {
    }

    /**
     * Told how far along the download is so the UI can show it.
     */
    public interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    /**
     * What should happen once the file is on disk.
     */
    public enum Handling {
        /**
         * Run it; it knows how to upgrade the existing install. The launcher has to quit
         * first, or the files it is holding open cannot be replaced.
         */
        RUN_INSTALLER,
        /**
         * Show it to the user and let them take it from there.
         */
        REVEAL_FILE
    }

    /**
     * The file to fetch for this machine, and what to do with it afterwards.
     */
    public static final class Plan {
        private final SelfUpdateChecker.Asset asset;
        private final Handling handling;

        Plan(SelfUpdateChecker.Asset asset, Handling handling) {
            this.asset = asset;
            this.handling = handling;
        }

        public SelfUpdateChecker.Asset getAsset() {
            return asset;
        }

        public Handling getHandling() {
            return handling;
        }
    }

    /**
     * Works out which of the release's files belongs on this machine.
     *
     * @return {@code null} when nothing matches, which is the signal to fall back to
     * opening the release page rather than guessing
     */
    public static Plan planFor(SelfUpdateChecker.LatestRelease release) {
        if (release == null || release.getAssets().isEmpty()) {
            return null;
        }
        if (OS.WINDOWS.isCurrent()) {
            if (isInstalledCopy()) {
                SelfUpdateChecker.Asset installer = find(release, "windows", ".exe");
                if (installer != null) {
                    return new Plan(installer, Handling.RUN_INSTALLER);
                }
            }
            SelfUpdateChecker.Asset portable = find(release, "windows", ".zip");
            return portable == null ? null : new Plan(portable, Handling.REVEAL_FILE);
        }
        if (OS.OSX.isCurrent()) {
            // the two Mac builds differ by architecture, and running the Intel one under
            // Rosetta when an arm64 build exists would be a downgrade in all but name
            String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            boolean appleSilicon = arch.contains("aarch64") || arch.contains("arm");
            SelfUpdateChecker.Asset dmg = find(release, appleSilicon ? "apple_silicon" : "intel", ".dmg");
            if (dmg == null) {
                dmg = find(release, "macos", ".dmg");
            }
            return dmg == null ? null : new Plan(dmg, Handling.REVEAL_FILE);
        }
        if (OS.LINUX.isCurrent()) {
            SelfUpdateChecker.Asset archive = find(release, "linux", ".tar.gz");
            return archive == null ? null : new Plan(archive, Handling.REVEAL_FILE);
        }
        return null;
    }

    private static SelfUpdateChecker.Asset find(SelfUpdateChecker.LatestRelease release,
                                                String contains, String suffix) {
        for (SelfUpdateChecker.Asset asset : release.getAssets()) {
            String name = asset.getName().toLowerCase(Locale.ROOT);
            if (name.contains(contains) && name.endsWith(suffix)) {
                return asset;
            }
        }
        return null;
    }

    /**
     * Whether this copy came from the installer. The portable build unpacks the same files
     * without an uninstaller beside them.
     */
    static boolean isInstalledCopy() {
        File here = new File(System.getProperty("user.dir", "."));
        return new File(here, UNINSTALLER).isFile();
    }

    /**
     * Downloads the planned file next to wherever the launcher keeps its own data.
     *
     * @return the file on disk, ready to be run or shown
     * @throws IOException if the download fails or arrives incomplete
     */
    public static File download(Plan plan, File directory, ProgressListener listener) throws IOException {
        SelfUpdateChecker.Asset asset = plan.getAsset();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory);
        }
        File target = new File(directory, asset.getName());
        File partial = new File(directory, asset.getName() + ".part");

        HttpURLConnection connection = (HttpURLConnection) new URL(asset.getUrl()).openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/octet-stream");

        long expected = asset.getSize() > 0 ? asset.getSize() : connection.getContentLengthLong();
        long done = 0;
        try (InputStream in = connection.getInputStream();
             OutputStream out = new FileOutputStream(partial)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                done += read;
                if (listener != null) {
                    listener.onProgress(done, expected);
                }
            }
        } finally {
            connection.disconnect();
        }

        // a truncated installer is worse than none: it would run and fail partway through
        if (expected > 0 && done != expected) {
            if (!partial.delete()) {
                partial.deleteOnExit();
            }
            throw new IOException("Download stopped early: got " + done + " of " + expected + " bytes");
        }
        if (target.isFile() && !target.delete()) {
            throw new IOException("Could not replace " + target);
        }
        if (!partial.renameTo(target)) {
            throw new IOException("Could not move the download into place");
        }
        return target;
    }

    /**
     * Starts the installer and reports whether it took. The caller is expected to shut the
     * launcher down straight afterwards - the installer cannot replace files this process
     * still has open.
     */
    public static boolean runInstaller(File installer) {
        try {
            new ProcessBuilder(installer.getAbsolutePath()).start();
            return true;
        } catch (IOException e) {
            log.warn("Could not start the installer {}", installer, e);
            return false;
        }
    }

    /**
     * Opens the folder holding the downloaded file.
     */
    public static void reveal(File file) {
        File folder = file.getParentFile();
        OS.openFolder(folder == null ? file : folder);
    }
}
