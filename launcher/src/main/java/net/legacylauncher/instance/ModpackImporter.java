package net.legacylauncher.instance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.modrinth.ContentFile;
import net.legacylauncher.modrinth.ContentType;
import net.legacylauncher.modrinth.CurseForgeApi;
import net.legacylauncher.modrinth.CurseForgeProvider;
import net.legacylauncher.modrinth.ModLoader;
import net.legacylauncher.modrinth.ModTarget;
import net.legacylauncher.util.EHttpClient;
import net.legacylauncher.util.FileUtil;
import net.legacylauncher.util.ua.LauncherUserAgent;
import net.minecraft.launcher.updater.VersionSyncInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpHeaders;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns a modpack file into a new {@link Instance} - either a Modrinth {@code .mrpack}
 * (the ecosystem-standard format: an index of files to download plus an
 * {@code overrides/} folder of local files to copy in), or this launcher's own exported
 * instance zip (a plain copy of the whole instance folder, produced by
 * {@link InstanceManager#export}).
 * <p>
 * All methods block on network and disk I/O, so callers must stay off the Swing thread.
 */
@Slf4j
public final class ModpackImporter {
    private ModpackImporter() {
    }

    public interface ProgressListener {
        void onStep(String message, int current, int total);
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
     * @throws IOException when the file is not a pack this launcher understands
     */
    public static Instance importAny(File file, InstanceManager manager, ProgressListener listener)
            throws IOException {
        switch (detectFormat(file)) {
            case MRPACK:
                return importMrpack(file, manager, listener);
            case CURSEFORGE:
                return importCurseForge(file, manager, listener);
            case LEGACY_EXPORT:
                return importLegacyExport(file, manager);
            default:
                throw new IOException("unrecognised modpack format");
        }
    }

    /**
     * Fetches a modpack the user picked out of a library into a scratch file, ready for
     * {@link #importAny}. The caller owns the returned file and should delete it.
     */
    public static File downloadToTemp(ContentFile file) throws IOException {
        if (StringUtils.isEmpty(file.getUrl())) {
            throw new IOException("no download link for " + file.getFileName()
                    + " - its author opted out of third-party downloads");
        }
        File temp = Files.createTempFile("ll-modpack-", ".zip").toFile();
        try {
            Files.write(temp.toPath(), download(file.getUrl()));
        } catch (IOException e) {
            temp.delete();
            throw e;
        }
        return temp;
    }

    // ---------------------------------------------------------------- .mrpack

    public static Instance importMrpack(File mrpackFile, InstanceManager manager, ProgressListener listener)
            throws IOException {
        JsonObject index;
        try (ZipFile zip = new ZipFile(mrpackFile)) {
            ZipEntry entry = zip.getEntry("modrinth.index.json");
            if (entry == null) {
                throw new IOException("not a .mrpack: no modrinth.index.json");
            }
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                index = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }

        String name = index.has("name") ? index.get("name").getAsString() : mrpackFile.getName();
        String versionId = resolveVersionId(index);

        Instance instance = manager.create(name, versionId);
        File gameDir = instance.getGameDir();

        try {
            JsonArray files = index.has("files") ? index.getAsJsonArray("files") : new JsonArray();
            int total = files.size();
            int current = 0;
            for (JsonElement e : files) {
                current++;
                JsonObject file = e.getAsJsonObject();
                if (isUnsupportedForClient(file)) {
                    continue;
                }
                String path = file.get("path").getAsString();
                if (listener != null) {
                    listener.onStep(path, current, total);
                }
                downloadFile(file, gameDir);
            }
            try (ZipFile zip = new ZipFile(mrpackFile)) {
                extractPrefixed(zip, "overrides/", gameDir);
                extractPrefixed(zip, "client-overrides/", gameDir);
            }
        } catch (IOException e) {
            // half-installed modpacks are worse than none - the user can just try again
            FileUtil.deleteDirectory(instance.getFolder());
            throw e;
        }
        return instance;
    }

    private static boolean isUnsupportedForClient(JsonObject file) {
        if (!file.has("env")) {
            return false;
        }
        JsonObject env = file.getAsJsonObject("env");
        return env.has("client") && "unsupported".equals(env.get("client").getAsString());
    }

    private static void downloadFile(JsonObject file, File gameDir) throws IOException {
        String path = file.get("path").getAsString();
        File destination = new File(gameDir, path).getCanonicalFile();
        if (!destination.toPath().startsWith(gameDir.getCanonicalFile().toPath())) {
            throw new IOException("refusing to write outside the instance folder: " + path);
        }
        FileUtil.createFolder(destination.getParentFile());

        JsonArray downloads = file.has("downloads") ? file.getAsJsonArray("downloads") : new JsonArray();
        if (downloads.isEmpty()) {
            throw new IOException("no download URL for " + path);
        }

        IOException lastError = null;
        for (JsonElement urlElement : downloads) {
            String url = urlElement.getAsString();
            try {
                byte[] bytes = download(url);
                verifyHash(path, bytes, file);
                File temp = new File(destination.getParentFile(), destination.getName() + ".part");
                Files.write(temp.toPath(), bytes);
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                lastError = e;
                log.warn("Could not download {} from {}, trying the next mirror if any", path, url, e);
            }
        }
        throw lastError != null ? lastError : new IOException("could not download " + path);
    }

    private static byte[] download(String url) throws IOException {
        Content content = EHttpClient.toContent(
                Request.get(url).addHeader(HttpHeaders.USER_AGENT, LauncherUserAgent.USER_AGENT));
        if (content == null) {
            throw new IOException("no content received for " + url);
        }
        return content.asBytes();
    }

    private static void verifyHash(String path, byte[] bytes, JsonObject file) throws IOException {
        if (!file.has("hashes")) {
            return;
        }
        JsonObject hashes = file.getAsJsonObject("hashes");
        String expected = hashes.has("sha1") ? hashes.get("sha1").getAsString() : null;
        if (StringUtils.isEmpty(expected)) {
            return;
        }
        String actual = sha1(bytes);
        if (!expected.equalsIgnoreCase(actual)) {
            throw new IOException(path + " does not match its published SHA-1 hash");
        }
    }

    private static String sha1(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by the Java platform", e);
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

    private static void extractPrefixed(ZipFile zip, String prefix, File targetDir) throws IOException {
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                continue;
            }
            String relative = entry.getName().substring(prefix.length());
            if (relative.isEmpty()) {
                continue;
            }
            File destination = new File(targetDir, relative).getCanonicalFile();
            if (!destination.toPath().startsWith(targetDir.getCanonicalFile().toPath())) {
                throw new IOException("refusing to write outside the instance folder: " + entry.getName());
            }
            FileUtil.createFolder(destination.getParentFile());
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Matches the pack's {@code dependencies} (a Minecraft version plus, usually, a mod
     * loader) against the launcher's own version list, the same way
     * {@code NewInstanceDialog} matches a manually picked version and loader - taking the
     * newest launcher version id that provides that combination.
     */
    private static String resolveVersionId(JsonObject index) throws IOException {
        if (!index.has("dependencies")) {
            throw new IOException("modpack has no \"dependencies\" (no Minecraft version)");
        }
        JsonObject dependencies = index.getAsJsonObject("dependencies");
        if (!dependencies.has("minecraft")) {
            throw new IOException("modpack does not declare a Minecraft version");
        }
        String gameVersion = dependencies.get("minecraft").getAsString();

        ModLoader loader = null;
        for (Entry<String, JsonElement> entry : dependencies.entrySet()) {
            ModLoader detected = ModLoader.detect(entry.getKey());
            if (detected != null) {
                loader = detected;
                break;
            }
        }
        return resolveVersionId(gameVersion, loader);
    }

    /**
     * Picks the launcher version id that provides this Minecraft version on this loader,
     * the same way {@code NewInstanceDialog} resolves a manually chosen pair.
     */
    private static String resolveVersionId(String gameVersion, ModLoader loader) throws IOException {
        for (VersionSyncInfo info : LegacyLauncher.getInstance().getVersionManager().getVersions(false)) {
            String id = info.getID();
            String idGameVersion = ModTarget.extractGameVersion(id);
            if (!gameVersion.equals(idGameVersion)) {
                continue;
            }
            ModLoader idLoader = ModLoader.detect(id);
            if (idLoader == loader) {
                return id;
            }
        }
        throw new IOException("no installable version found for Minecraft " + gameVersion
                + (loader != null ? " (" + loader.getDisplayName() + ")" : ""));
    }

    // ---------------------------------------------------------------- CurseForge

    /**
     * Imports a CurseForge modpack zip: a {@code manifest.json} naming every mod by
     * CurseForge project and file id, plus an overrides folder of loose files to copy in.
     * <p>
     * Unlike a {@code .mrpack}, the manifest carries no download links of its own, so each
     * file has to be resolved through CurseForge's API first - which needs an API key.
     */
    public static Instance importCurseForge(File zipFile, InstanceManager manager, ProgressListener listener)
            throws IOException {
        String apiKey = CurseForgeProvider.getApiKey();
        if (StringUtils.isEmpty(apiKey)) {
            throw new IOException("a CurseForge API key is needed to import a CurseForge modpack");
        }

        JsonObject manifest;
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                throw new IOException("not a CurseForge modpack: no manifest.json");
            }
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                manifest = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }

        JsonObject minecraft = manifest.has("minecraft") ? manifest.getAsJsonObject("minecraft") : null;
        if (minecraft == null || !minecraft.has("version")) {
            throw new IOException("modpack does not declare a Minecraft version");
        }
        String name = manifest.has("name") ? manifest.get("name").getAsString() : zipFile.getName();
        String versionId = resolveVersionId(minecraft.get("version").getAsString(), curseForgeLoader(minecraft));

        Instance instance = manager.create(name, versionId);
        File gameDir = instance.getGameDir();

        try {
            JsonArray files = manifest.has("files") ? manifest.getAsJsonArray("files") : new JsonArray();
            File modsDir = new File(gameDir, ContentType.MOD.getFolder());
            int total = files.size();
            int current = 0;
            for (JsonElement e : files) {
                current++;
                JsonObject entry = e.getAsJsonObject();
                long projectId = entry.get("projectID").getAsLong();
                long fileId = entry.get("fileID").getAsLong();

                CurseForgeApi.ModFile file = CurseForgeApi.getFile(apiKey, projectId, fileId).data;
                if (file == null) {
                    throw new IOException("CurseForge does not know file " + fileId + " of project " + projectId);
                }
                if (StringUtils.isEmpty(file.downloadUrl)) {
                    // the author opted out of third-party downloads - there is nothing the
                    // launcher can do but name the file so it can be fetched by hand
                    throw new IOException("CurseForge will not serve \"" + file.fileName
                            + "\" to third-party apps. Download it by hand into " + modsDir);
                }
                if (listener != null) {
                    listener.onStep(file.fileName, current, total);
                }

                byte[] bytes = download(file.downloadUrl);
                String expected = file.hash(1); // CurseForge algo 1 = sha1
                if (StringUtils.isNotEmpty(expected) && !expected.equalsIgnoreCase(sha1(bytes))) {
                    throw new IOException(file.fileName + " does not match its published SHA-1 hash");
                }
                writeInto(modsDir, file.fileName, bytes);
            }

            String overrides = manifest.has("overrides") ? manifest.get("overrides").getAsString() : "overrides";
            try (ZipFile zip = new ZipFile(zipFile)) {
                extractPrefixed(zip, overrides.endsWith("/") ? overrides : overrides + "/", gameDir);
            }
        } catch (IOException e) {
            // a half-installed modpack is worse than none - the user can just try again
            FileUtil.deleteDirectory(instance.getFolder());
            throw e;
        }
        return instance;
    }

    /**
     * @return the pack's primary mod loader, or the first recognisable one when none is
     * flagged primary; {@code null} for a vanilla pack
     */
    private static ModLoader curseForgeLoader(JsonObject minecraft) {
        if (!minecraft.has("modLoaders")) {
            return null;
        }
        ModLoader first = null;
        for (JsonElement e : minecraft.getAsJsonArray("modLoaders")) {
            JsonObject loader = e.getAsJsonObject();
            if (!loader.has("id")) {
                continue;
            }
            ModLoader detected = ModLoader.detect(loader.get("id").getAsString());
            if (detected == null) {
                continue;
            }
            if (loader.has("primary") && loader.get("primary").getAsBoolean()) {
                return detected;
            }
            if (first == null) {
                first = detected;
            }
        }
        return first;
    }

    /**
     * Writes one downloaded file into a folder, refusing a name that would escape it.
     */
    private static void writeInto(File dir, String fileName, byte[] bytes) throws IOException {
        FileUtil.createFolder(dir);
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
        File temp = new File(destination.getParentFile(), destination.getName() + ".part");
        try {
            Files.write(temp.toPath(), bytes);
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            temp.delete();
        }
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
