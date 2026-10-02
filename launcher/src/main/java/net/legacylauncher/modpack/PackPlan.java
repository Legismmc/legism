package net.legacylauncher.modpack;

import net.legacylauncher.modrinth.ModLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything needed to put one version of a modpack on disk, worked out ahead of time and
 * independent of where the pack came from.
 * <p>
 * Every library describes its packs differently - Modrinth and CurseForge ship a zip with
 * an index inside, FTB lists thousands of loose files, Technic hands out zips to unpack,
 * ATLauncher mixes all of these - but what has to happen in the end is always the same:
 * download some files to some paths, unpack some archives, copy in some local files. Each
 * source turns its own format into one of these, and {@link PackInstaller} does the rest.
 * <p>
 * All paths are relative to the game directory and use forward slashes.
 */
public final class PackPlan {
    private final String name;
    private final String versionName;
    private final String gameVersion;
    private final ModLoader loader;
    private final String loaderVersion;
    private final List<Download> downloads = new ArrayList<>();
    private final List<Overlay> overlays = new ArrayList<>();
    private final List<Skipped> skipped = new ArrayList<>();
    private final List<File> scratch = new ArrayList<>();

    /**
     * @param name          what the pack calls itself, used as the instance name
     * @param versionName   the pack's own version label, or {@code null}
     * @param gameVersion   the Minecraft version it runs on
     * @param loader        the mod loader, or {@code null} for a vanilla pack
     * @param loaderVersion the loader version the pack asks for, or {@code null}; only a
     *                      preference, since the launcher installs loaders from its own list
     */
    public PackPlan(String name, String versionName, String gameVersion, ModLoader loader, String loaderVersion) {
        this.name = name;
        this.versionName = versionName;
        this.gameVersion = gameVersion;
        this.loader = loader;
        this.loaderVersion = loaderVersion;
    }

    public String getName() {
        return name;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public ModLoader getLoader() {
        return loader;
    }

    public String getLoaderVersion() {
        return loaderVersion;
    }

    public List<Download> getDownloads() {
        return downloads;
    }

    public List<Overlay> getOverlays() {
        return overlays;
    }

    public List<Skipped> getSkipped() {
        return skipped;
    }

    /**
     * Temporary files that only exist for this plan - a downloaded pack zip its overlays
     * read from, say - and are deleted once it has been carried out or abandoned.
     */
    public void addScratch(File file) {
        scratch.add(file);
    }

    public void cleanup() {
        for (File file : scratch) {
            if (file != null && file.exists() && !file.delete()) {
                file.deleteOnExit();
            }
        }
        scratch.clear();
    }

    /**
     * One file to fetch. Either saved as-is at {@link #getPath()}, or - for an archive -
     * unpacked into the folder at {@link #getPath()}.
     */
    public static final class Download {
        private final List<String> urls;
        private final String path;
        private final String hashAlgorithm;
        private final String hash;
        private final long size;
        private final boolean extract;
        private final List<String> skipPrefixes;

        private Download(List<String> urls, String path, String hashAlgorithm, String hash, long size,
                         boolean extract, List<String> skipPrefixes) {
            this.urls = urls;
            this.path = PackPaths.normalize(path);
            this.hashAlgorithm = hashAlgorithm;
            this.hash = hash;
            this.size = size;
            this.extract = extract;
            this.skipPrefixes = skipPrefixes;
        }

        /**
         * A file saved under its own path.
         *
         * @param urls          where to get it, tried in order
         * @param hashAlgorithm a {@link java.security.MessageDigest} name such as
         *                      {@code SHA-1} or {@code MD5}, or {@code null} for no check
         */
        public static Download file(List<String> urls, String path, String hashAlgorithm, String hash, long size) {
            return new Download(urls, path, hashAlgorithm, hash, size, false, Collections.<String>emptyList());
        }

        public static Download file(String url, String path, String hashAlgorithm, String hash, long size) {
            return file(Collections.singletonList(url), path, hashAlgorithm, hash, size);
        }

        /**
         * An archive whose contents go into the folder {@code into} ({@code ""} for the
         * game directory itself), leaving out any entry starting with one of
         * {@code skipPrefixes}.
         */
        public static Download archive(String url, String into, String hashAlgorithm, String hash, long size,
                                       List<String> skipPrefixes) {
            return new Download(Collections.singletonList(url), into, hashAlgorithm, hash, size, true,
                    skipPrefixes == null ? Collections.<String>emptyList() : skipPrefixes);
        }

        public List<String> getUrls() {
            return urls;
        }

        public String getPath() {
            return path;
        }

        public String getHashAlgorithm() {
            return hashAlgorithm;
        }

        public String getHash() {
            return hash;
        }

        public long getSize() {
            return size;
        }

        public boolean isExtract() {
            return extract;
        }

        public List<String> getSkipPrefixes() {
            return skipPrefixes;
        }

        /**
         * @return the last path segment, for showing which file is downloading
         */
        public String getDisplayName() {
            int slash = path.lastIndexOf('/');
            String name = slash >= 0 ? path.substring(slash + 1) : path;
            if (!name.isEmpty() && !extract) {
                return name;
            }
            String url = urls.isEmpty() ? "" : urls.get(0);
            int urlSlash = url.lastIndexOf('/');
            return urlSlash >= 0 ? url.substring(urlSlash + 1) : url;
        }
    }

    /**
     * Entries of a local zip copied in after the downloads - the {@code overrides/}
     * folder of a Modrinth or CurseForge pack, for instance. Entries under
     * {@code prefix} land at their path with the prefix taken off.
     */
    public static final class Overlay {
        private final File zip;
        private final String prefix;
        private final List<String> skipPrefixes;

        public Overlay(File zip, String prefix, List<String> skipPrefixes) {
            this.zip = zip;
            this.prefix = prefix == null ? "" : prefix;
            this.skipPrefixes = skipPrefixes == null ? Collections.<String>emptyList() : skipPrefixes;
        }

        public File getZip() {
            return zip;
        }

        public String getPrefix() {
            return prefix;
        }

        public List<String> getSkipPrefixes() {
            return skipPrefixes;
        }
    }

    /**
     * A file the pack needs that cannot be fetched automatically, to be listed for the
     * user instead.
     */
    public static final class Skipped {
        private final String name;
        private final String fileName;
        private final String pageUrl;
        private final String sha1;
        private final String folder;

        /**
         * @param folder where the file belongs, relative to the game directory
         */
        public Skipped(String name, String fileName, String pageUrl, String sha1, String folder) {
            this.name = name;
            this.fileName = fileName;
            this.pageUrl = pageUrl;
            this.sha1 = sha1;
            this.folder = PackPaths.normalize(folder);
        }

        public String getName() {
            return name;
        }

        public String getFileName() {
            return fileName;
        }

        public String getPageUrl() {
            return pageUrl;
        }

        public String getSha1() {
            return sha1;
        }

        public String getFolder() {
            return folder;
        }
    }
}
