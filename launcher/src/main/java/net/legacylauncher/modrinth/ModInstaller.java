package net.legacylauncher.modrinth;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.EHttpClient;
import net.legacylauncher.util.FileUtil;
import net.legacylauncher.util.ua.LauncherUserAgent;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpHeaders;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Downloads Modrinth mods into a game directory and manages what is already there.
 * <p>
 * Every download is checked against the SHA-512 Modrinth publishes for the file; a jar
 * that does not match is discarded rather than installed.
 * <p>
 * All methods block, so callers must stay off the Swing thread.
 */
@Slf4j
public class ModInstaller {
    /**
     * How deep the installer follows required dependencies. Modrinth dependency chains
     * are shallow in practice; the limit is there so a cycle in the metadata cannot turn
     * into an endless download.
     */
    private static final int MAX_DEPENDENCY_DEPTH = 5;

    private final ModTarget target;
    private final ContentType type;

    public ModInstaller(ModTarget target, ContentType type) {
        this.target = target;
        this.type = type;
    }

    /**
     * Convenience for the common case of installing loader mods.
     */
    public ModInstaller(ModTarget target) {
        this(target, ContentType.MOD);
    }

    public ContentType getType() {
        return type;
    }

    /**
     * The folder this installer writes into, e.g. {@code <gameDir>/shaderpacks}.
     */
    public File getDirectory() {
        return target.getDirectory(type);
    }

    /**
     * Reports what the installer is doing so a UI can follow along.
     */
    public interface Listener {
        void onStep(String fileName, int current, int total);
    }

    /**
     * Installs one version and, optionally, everything it declares as required.
     *
     * @return the names of the files that ended up in the mods directory
     */
    public List<String> install(ModrinthVersion version,
                                boolean withDependencies,
                                Listener listener) throws IOException {
        List<ModrinthVersion> plan = new ArrayList<>();
        plan.add(version);
        if (withDependencies) {
            collectDependencies(version, plan, new HashSet<String>(), 0);
        }

        FileUtil.createFolder(getDirectory());

        List<String> installed = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            ModrinthVersion current = plan.get(i);
            ModrinthFile file = current.getPrimaryFile();
            if (file == null) {
                log.warn("Version {} carries no downloadable file, skipping", current);
                continue;
            }
            if (listener != null) {
                listener.onStep(file.getFilename(), i + 1, plan.size());
            }
            downloadInto(file, getDirectory());
            installed.add(file.getFilename());
        }
        return installed;
    }

    /**
     * Walks the required dependencies of the given version, appending the versions that
     * have to be installed alongside it.
     */
    private void collectDependencies(ModrinthVersion version,
                                     List<ModrinthVersion> plan,
                                     Set<String> visitedProjects,
                                     int depth) {
        if (depth >= MAX_DEPENDENCY_DEPTH) {
            log.warn("Stopping dependency resolution at depth {}", depth);
            return;
        }
        for (ModrinthDependency dependency : version.getDependencies()) {
            if (!dependency.isRequired()) {
                continue;
            }
            try {
                ModrinthVersion resolved = resolve(dependency);
                if (resolved == null) {
                    log.warn("Could not resolve required dependency {} of {}", dependency, version);
                    continue;
                }
                if (resolved.getProjectId() != null && !visitedProjects.add(resolved.getProjectId())) {
                    continue;
                }
                if (containsSameFile(plan, resolved)) {
                    continue;
                }
                plan.add(resolved);
                collectDependencies(resolved, plan, visitedProjects, depth + 1);
            } catch (IOException e) {
                log.warn("Could not resolve required dependency {}: {}", dependency, e.toString());
            }
        }
    }

    private ModrinthVersion resolve(ModrinthDependency dependency) throws IOException {
        if (StringUtils.isNotEmpty(dependency.getVersionId())) {
            return ModrinthApi.getVersion(dependency.getVersionId());
        }
        if (StringUtils.isEmpty(dependency.getProjectId())) {
            return null;
        }
        List<ModrinthVersion> candidates = ModrinthApi.listVersions(
                type,
                dependency.getProjectId(),
                target.getGameVersion(),
                target.getLoader() == null ? null : target.getLoader().getId()
        );
        return pickBest(candidates);
    }

    private static boolean containsSameFile(List<ModrinthVersion> plan, ModrinthVersion candidate) {
        ModrinthFile candidateFile = candidate.getPrimaryFile();
        if (candidateFile == null) {
            return true; // nothing to install anyway
        }
        for (ModrinthVersion planned : plan) {
            ModrinthFile file = planned.getPrimaryFile();
            if (file != null && file.getFilename() != null
                    && file.getFilename().equals(candidateFile.getFilename())) {
                return true;
            }
        }
        return false;
    }

    private void downloadInto(ModrinthFile file, File modsDir) throws IOException {
        String fileName = sanitizeFileName(file.getFilename(), type);
        File destination = new File(modsDir, fileName);

        log.info("Downloading {} ({} bytes) into {}", fileName, file.getSize(), modsDir);

        Content content;
        try {
            content = EHttpClient.toContent(
                    Request.get(file.getUrl())
                            .addHeader(HttpHeaders.USER_AGENT, LauncherUserAgent.USER_AGENT)
            );
        } catch (IOException e) {
            throw new ModrinthException("could not download " + fileName + ": " + e.getMessage(), e);
        }
        if (content == null) {
            throw new ModrinthException("no content received for " + fileName);
        }

        byte[] bytes = content.asBytes();
        verify(fileName, bytes, file);

        File temp = new File(modsDir, fileName + ".part");
        try {
            Files.write(temp.toPath(), bytes);
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            temp.delete();
        }

        // a copy the user disabled earlier would otherwise sit next to the fresh one
        File disabled = new File(modsDir, fileName + InstalledMod.DISABLED_SUFFIX);
        if (disabled.isFile()) {
            disabled.delete();
        }

        log.info("Installed {}", destination);
    }

    private static void verify(String fileName, byte[] bytes, ModrinthFile file) throws ModrinthException {
        String expected = file.getSha512();
        String algorithm = "SHA-512";
        if (StringUtils.isEmpty(expected)) {
            expected = file.getSha1();
            algorithm = "SHA-1";
        }
        if (StringUtils.isEmpty(expected)) {
            log.warn("Modrinth published no hash for {}, installing unverified", fileName);
            return;
        }
        String actual = digest(bytes, algorithm);
        if (!expected.equalsIgnoreCase(actual)) {
            throw new ModrinthException(fileName + " does not match the " + algorithm
                    + " hash published by Modrinth (expected " + expected + ", got " + actual + ")");
        }
    }

    private static String digest(byte[] bytes, String algorithm) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is required by the Java platform", e);
        }
        byte[] hash = digest.digest(bytes);
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                result.append('0');
            }
            result.append(hex);
        }
        return result.toString();
    }

    /**
     * Keeps a hostile file name from escaping the target directory, and refuses anything
     * whose extension does not match the content type. Modrinth validates its own
     * uploads, but the name arrives over the network, so it is not trusted here.
     */
    static String sanitizeFileName(String fileName, ContentType type) throws ModrinthException {
        if (StringUtils.isEmpty(fileName)) {
            throw new ModrinthException("Modrinth sent a file without a name");
        }
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new ModrinthException("Modrinth sent an unusable file name: " + fileName);
        }
        if (!type.accepts(name)) {
            throw new ModrinthException("refusing to install " + fileName + " as a "
                    + type.getModrinthType() + ": expected one of " + type.getExtensions());
        }
        return name;
    }

    /**
     * @return every file of this content type in the target directory, enabled or not,
     * sorted by name
     */
    public List<InstalledMod> listInstalled() {
        File[] files = getDirectory().listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<InstalledMod> mods = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name.endsWith(InstalledMod.DISABLED_SUFFIX)) {
                name = name.substring(0, name.length() - InstalledMod.DISABLED_SUFFIX.length());
            }
            if (type.accepts(name)) {
                mods.add(new InstalledMod(file));
            }
        }
        Collections.sort(mods, new Comparator<InstalledMod>() {
            @Override
            public int compare(InstalledMod a, InstalledMod b) {
                return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
            }
        });
        return mods;
    }

    /**
     * @return true when the mods directory already holds a file with this name, enabled
     * or disabled
     */
    public boolean isInstalled(String fileName) {
        File modsDir = getDirectory();
        return new File(modsDir, fileName).isFile()
                || new File(modsDir, fileName + InstalledMod.DISABLED_SUFFIX).isFile();
    }

    /**
     * @return true when any of the files of the given version is already there
     */
    public boolean isInstalled(ModrinthVersion version) {
        for (ModrinthFile file : version.getFiles()) {
            if (file.getFilename() != null && isInstalled(file.getFilename())) {
                return true;
            }
        }
        return false;
    }

    public void delete(InstalledMod mod) throws IOException {
        if (!mod.getFile().delete() && mod.getFile().exists()) {
            throw new IOException("could not delete " + mod.getFile());
        }
        log.info("Deleted {}", mod.getFile());
    }

    /**
     * Renames a mod between name.jar and name.jar.disabled.
     *
     * @return the mod at its new path
     */
    public InstalledMod setEnabled(InstalledMod mod, boolean enabled) throws IOException {
        if (mod.isEnabled() == enabled) {
            return mod;
        }
        String name = enabled
                ? mod.getDisplayName()
                : mod.getDisplayName() + InstalledMod.DISABLED_SUFFIX;
        File destination = new File(mod.getFile().getParentFile(), name);
        Files.move(mod.getFile().toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("{} {}", enabled ? "Enabled" : "Disabled", destination);
        return new InstalledMod(destination);
    }

    /**
     * Picks the newest version that fits the target, preferring a release over a beta or
     * an alpha when both are offered. Modrinth returns versions newest first.
     */
    public static ModrinthVersion pickBest(List<ModrinthVersion> versions) {
        ModrinthVersion fallback = null;
        for (ModrinthVersion version : versions) {
            if (version.getPrimaryFile() == null) {
                continue;
            }
            if ("release".equals(version.getVersionType())) {
                return version;
            }
            if (fallback == null) {
                fallback = version;
            }
        }
        return fallback;
    }

    /**
     * Human readable file size, e.g. 4.2 MB.
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024d;
        if (kb < 1024d) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024d;
        if (mb < 1024d) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(Locale.ROOT, "%.1f GB", mb / 1024d);
    }

    /**
     * Human readable download counter, e.g. 1.2M.
     */
    public static String formatCount(long count) {
        if (count < 1000L) {
            return String.valueOf(count);
        }
        if (count < 1000000L) {
            return String.format(Locale.ROOT, "%.1fK", count / 1000d);
        }
        return String.format(Locale.ROOT, "%.1fM", count / 1000000d);
    }
}
