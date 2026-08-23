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
import java.util.List;
import java.util.Locale;

/**
 * Downloads content into a game directory and manages what is already there.
 * <p>
 * Which files to fetch is decided by a {@link ContentProvider}; this class only puts them
 * on disk. Every download is checked against whatever hash its library publishes, and a
 * file that does not match is discarded rather than installed.
 * <p>
 * All methods block, so callers must stay off the Swing thread.
 */
@Slf4j
public class ModInstaller {

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
     * Downloads everything in the plan into the target folder.
     *
     * @return the names of the files that ended up there
     */
    public List<String> install(List<ContentFile> plan, Listener listener) throws IOException {
        FileUtil.createFolder(getDirectory());

        List<String> installed = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            ContentFile file = plan.get(i);
            if (listener != null) {
                listener.onStep(file.getFileName(), i + 1, plan.size());
            }
            downloadInto(file, getDirectory());
            installed.add(file.getFileName());
        }
        return installed;
    }

    private void downloadInto(ContentFile file, File targetDir) throws IOException {
        String fileName = sanitizeFileName(file.getFileName(), type);
        File destination = new File(targetDir, fileName);

        if (StringUtils.isEmpty(file.getUrl())) {
            throw new ModrinthException("no download link for " + fileName);
        }

        log.info("Downloading {} ({} bytes) into {}", fileName, file.getSize(), targetDir);

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

        File temp = new File(targetDir, fileName + ".part");
        try {
            Files.write(temp.toPath(), bytes);
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            temp.delete();
        }

        // a copy the user disabled earlier would otherwise sit next to the fresh one
        File disabled = new File(targetDir, fileName + InstalledMod.DISABLED_SUFFIX);
        if (disabled.isFile()) {
            disabled.delete();
        }

        log.info("Installed {}", destination);
    }

    /**
     * Checks the download against the strongest hash its library published for it.
     */
    private static void verify(String fileName, byte[] bytes, ContentFile file) throws ModrinthException {
        String expected = file.getSha512();
        String algorithm = "SHA-512";
        if (StringUtils.isEmpty(expected)) {
            expected = file.getSha1();
            algorithm = "SHA-1";
        }
        if (StringUtils.isEmpty(expected)) {
            expected = file.getMd5();
            algorithm = "MD5";
        }
        if (StringUtils.isEmpty(expected)) {
            log.warn("No hash was published for {}, installing unverified", fileName);
            return;
        }
        String actual = digest(bytes, algorithm);
        if (!expected.equalsIgnoreCase(actual)) {
            throw new ModrinthException(fileName + " does not match the published " + algorithm
                    + " hash (expected " + expected + ", got " + actual + ")");
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
     * whose extension does not match the content type. The libraries validate their own
     * uploads, but the name arrives over the network, so it is not trusted here.
     */
    static String sanitizeFileName(String fileName, ContentType type) throws ModrinthException {
        if (StringUtils.isEmpty(fileName)) {
            throw new ModrinthException("a file arrived without a name");
        }
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new ModrinthException("unusable file name: " + fileName);
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
     * @return true when the target directory already holds a file with this name, enabled
     * or disabled
     */
    public boolean isInstalled(String fileName) {
        File dir = getDirectory();
        return new File(dir, fileName).isFile()
                || new File(dir, fileName + InstalledMod.DISABLED_SUFFIX).isFile();
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
