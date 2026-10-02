package net.legacylauncher.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.modpack.PackFormats;
import net.legacylauncher.modpack.PackInstaller;
import net.legacylauncher.util.FileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns a modpack file into a new {@link Instance} - either a Modrinth {@code .mrpack}, a
 * CurseForge modpack zip, or this launcher's own exported instance zip (a plain copy of the
 * whole instance folder, produced by {@link InstanceManager#export}).
 * <p>
 * The actual installing is shared with the catalog and lives in
 * {@link net.legacylauncher.modpack.PackInstaller}; this class keeps the types every
 * install reports through.
 * <p>
 * All methods block on network and disk I/O, so callers must stay off the Swing thread.
 */
@Slf4j
public final class ModpackImporter {
    private ModpackImporter() {
    }

    /**
     * The phases an install goes through, in order. Not every pack goes through all of
     * them: a file picked from disk starts at {@link #RESOLVING}, and an exported instance
     * only ever extracts.
     */
    public enum Stage {
        DOWNLOADING_PACK, RESOLVING, DOWNLOADING_FILES, EXTRACTING
    }

    public interface ProgressListener {
        /**
         * Another of the pack's files has finished: {@code current} of {@code total} are
         * done. Sent once with {@code current} 0 before the first one starts.
         */
        void onStep(String message, int current, int total);

        default void onStage(Stage stage) {
        }

        /**
         * How many bytes have arrived - of the pack's own archive while that downloads,
         * of all its files together after that. {@code total} is not positive when nobody
         * said how big it is.
         */
        default void onBytes(long done, long total) {
        }

        /**
         * Checked between and during downloads; once this is true the install stops and
         * removes whatever it had made so far.
         */
        default boolean isCancelled() {
            return false;
        }
    }

    /**
     * Thrown when the listener asked to stop. Still an {@link IOException}, so callers
     * that only care whether the install worked need no extra catch - but the ones that
     * show an error should not show one for this.
     */
    public static final class CancelledException extends InterruptedIOException {
        public CancelledException() {
            super("cancelled");
        }
    }

    /**
     * A file the pack needs that could not be fetched automatically, because its author
     * does not let CurseForge hand it to anything other than CurseForge's own app. Prism
     * and the other launchers that respect that do the same thing this one does: install
     * everything else and send the player to the file's page for the rest.
     */
    public static final class SkippedFile {
        private final String name;
        private final String fileName;
        private final String pageUrl;
        private final String sha1;
        private final File folder;

        public SkippedFile(String name, String fileName, String pageUrl, String sha1, File folder) {
            this.name = name;
            this.fileName = fileName;
            this.pageUrl = pageUrl;
            this.sha1 = sha1;
            this.folder = folder;
        }

        /**
         * @return the project's name when known, otherwise the file name
         */
        public String getName() {
            return name;
        }

        public String getFileName() {
            return fileName;
        }

        /**
         * @return the page to download this exact file from by hand, or {@code null}
         */
        public String getPageUrl() {
            return pageUrl;
        }

        /**
         * @return the published SHA-1, or {@code null}; used to check a hand-downloaded copy
         */
        public String getSha1() {
            return sha1;
        }

        /**
         * @return the folder inside the instance this file belongs in
         */
        public File getFolder() {
            return folder;
        }

        /**
         * @return whether the file is already where it belongs - put there by hand
         */
        public boolean isInPlace() {
            return fileName != null && new File(folder, fileName).isFile();
        }
    }

    public static final class Result {
        private final Instance instance;
        private final List<SkippedFile> skipped;

        public Result(Instance instance, List<SkippedFile> skipped) {
            this.instance = instance;
            this.skipped = Collections.unmodifiableList(skipped);
        }

        public Instance getInstance() {
            return instance;
        }

        /**
         * @return the files still to be put in by hand; empty when the pack is complete
         */
        public List<SkippedFile> getSkipped() {
            return skipped;
        }
    }

    public enum Format {
        MRPACK, CURSEFORGE, LEGACY_EXPORT, UNKNOWN
    }

    /**
     * Peeks inside the zip to tell an {@code .mrpack} from this launcher's own export,
     * without extracting anything yet.
     */
    public static Format detectFormat(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            if (zip.getEntry("modrinth.index.json") != null) {
                return Format.MRPACK;
            }
            if (zip.getEntry("manifest.json") != null) {
                return Format.CURSEFORGE;
            }
            if (zip.getEntry(Instance.DESCRIPTOR) != null) {
                return Format.LEGACY_EXPORT;
            }
        } catch (IOException e) {
            log.debug("Could not peek {}: {}", file, e.toString());
        }
        return Format.UNKNOWN;
    }

    /**
     * Imports whichever kind of pack the file turns out to be.
     *
     * @throws IOException when the file is not a pack this launcher understands, or the
     *                     install failed - in which case nothing of it is left behind
     */
    public static Result importAny(File file, InstanceManager manager, ProgressListener listener)
            throws IOException {
        if (detectFormat(file) == Format.LEGACY_EXPORT) {
            if (listener != null) {
                listener.onStage(Stage.EXTRACTING);
            }
            return new Result(importLegacyExport(file, manager), Collections.<SkippedFile>emptyList());
        }
        return PackInstaller.installNew(PackFormats.read(file, listener), manager, null, listener);
    }

    /**
     * @return the SHA-1 of a file on disk, for checking a copy the user fetched by hand
     */
    public static String sha1(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by the Java platform", e);
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Where one file goes inside a folder, refusing a name that would escape it.
     */
    public static File target(File dir, String fileName) throws IOException {
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new IOException("unusable file name: " + fileName);
        }
        File destination = new File(dir, name).getCanonicalFile();
        if (!destination.toPath().startsWith(dir.getCanonicalFile().toPath())) {
            throw new IOException("refusing to write outside the instance folder: " + fileName);
        }
        return destination;
    }

    // ---------------------------------------------------------------- legacy export

    /**
     * Re-imports this launcher's own exported instance zip - a plain copy of the whole
     * instance folder, so there is nothing to download. Extracted into a scratch folder
     * first; {@link InstanceManager#importFolder} moves it into place under a fresh id.
     */
    public static Instance importLegacyExport(File zipFile, InstanceManager manager) throws IOException {
        File scratch = Files.createTempDirectory("ll-import-").toFile();
        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                File destination = new File(scratch, entry.getName()).getCanonicalFile();
                if (!destination.toPath().startsWith(scratch.getCanonicalFile().toPath())) {
                    throw new IOException("refusing to write outside the instance folder: " + entry.getName());
                }
                FileUtil.createFolder(destination.getParentFile());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return manager.importFolder(scratch);
        } catch (IOException e) {
            FileUtil.deleteDirectory(scratch);
            throw e;
        }
    }
}
