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
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns a modpack file into a new {@link Instance} - either a Modrinth {@code .mrpack}
 * (the ecosystem-standard format: an index of files to download plus an
 * {@code overrides/} folder of local files to copy in), a CurseForge modpack zip, or this
 * launcher's own exported instance zip (a plain copy of the whole instance folder,
 * produced by {@link InstanceManager#export}).
 * <p>
 * All methods block on network and disk I/O, so callers must stay off the Swing thread.
 */
@Slf4j
public final class ModpackImporter {
    /**
     * How many times one file is tried before the whole install gives up on it. A pack
     * is hundreds of downloads in a row, and on an ordinary home connection one of them
     * dropping is close to certain - which used to throw the entire pack away.
     */
    private static final int ATTEMPTS = 3;

    /**
     * Without a read timeout a connection that stalls - rather than drops - just sits
     * there, and the install looks frozen forever instead of retrying.
     */
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(30))
            .setResponseTimeout(Timeout.ofSeconds(60))
            .build();

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
         * One file of the pack is being fetched - {@code current} of {@code total}.
         */
        void onStep(String message, int current, int total);

        default void onStage(Stage stage) {
        }

        /**
         * How far along the file currently downloading is. {@code total} is not positive
         * when nobody said how big it is.
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

        SkippedFile(String name, String fileName, String pageUrl, String sha1, File folder) {
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

        Result(Instance instance, List<SkippedFile> skipped) {
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
        switch (detectFormat(file)) {
            case MRPACK:
                return new Result(importMrpack(file, manager, listener), Collections.<SkippedFile>emptyList());
            case CURSEFORGE:
                return importCurseForge(file, manager, listener);
            case LEGACY_EXPORT:
                stage(listener, Stage.EXTRACTING);
                return new Result(importLegacyExport(file, manager), Collections.<SkippedFile>emptyList());
            default:
                throw new IOException("unrecognised modpack format");
        }
    }

    /**
     * Fetches a modpack the user picked out of a library into a scratch file, ready for
     * {@link #importAny}. The caller owns the returned file and should delete it.
     */
    public static File downloadToTemp(ContentFile file, ProgressListener listener) throws IOException {
        if (StringUtils.isEmpty(file.getUrl())) {
            throw new IOException("no download link for " + file.getFileName()
                    + " - its author opted out of third-party downloads");
        }
        stage(listener, Stage.DOWNLOADING_PACK);
        File temp = Files.createTempFile("ll-modpack-", ".zip").toFile();
        try {
            fetch(file.getUrl(), temp, file.getSha1(), file.getSize(), listener);
        } catch (IOException e) {
            temp.delete();
            throw e;
        }
        return temp;
    }

    // ---------------------------------------------------------------- .mrpack

    public static Instance importMrpack(File mrpackFile, InstanceManager manager, ProgressListener listener)
            throws IOException {
        stage(listener, Stage.RESOLVING);
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
            JsonArray all = index.has("files") ? index.getAsJsonArray("files") : new JsonArray();
            List<JsonObject> files = new ArrayList<>();
            for (JsonElement e : all) {
                if (!isUnsupportedForClient(e.getAsJsonObject())) {
                    files.add(e.getAsJsonObject());
                }
            }

            stage(listener, Stage.DOWNLOADING_FILES);
            for (int i = 0; i < files.size(); i++) {
                JsonObject file = files.get(i);
                String path = file.get("path").getAsString();
                if (listener != null) {
                    listener.onStep(path, i + 1, files.size());
                }
                downloadFile(file, gameDir, listener);
            }

            stage(listener, Stage.EXTRACTING);
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

    private static void downloadFile(JsonObject file, File gameDir, ProgressListener listener) throws IOException {
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
        String sha1 = null;
        if (file.has("hashes") && file.getAsJsonObject("hashes").has("sha1")) {
            sha1 = file.getAsJsonObject("hashes").get("sha1").getAsString();
        }
        long size = file.has("fileSize") ? file.get("fileSize").getAsLong() : -1;

        IOException lastError = null;
        for (JsonElement urlElement : downloads) {
            String url = urlElement.getAsString();
            try {
                fetch(url, destination, sha1, size, listener);
                return;
            } catch (CancelledException e) {
                throw e;
            } catch (IOException e) {
                lastError = e;
                log.warn("Could not download {} from {}, trying the next mirror if any", path, url, e);
            }
        }
        throw lastError != null ? lastError : new IOException("could not download " + path);
    }

    // ---------------------------------------------------------------- downloading

    /**
     * Downloads one file to {@code destination}, retrying when the connection lets it down.
     * The file only appears under its real name once it has fully arrived and matches its
     * hash, so a failed or cancelled download never leaves a broken file behind.
     *
     * @param sha1 the expected SHA-1, or {@code null} to skip the check
     * @param size the expected size, or anything not positive when unknown; only used to
     *             show progress when the server does not say
     */
    private static void fetch(String url, File destination, String sha1, long size, ProgressListener listener)
            throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            checkCancelled(listener);
            try {
                fetchOnce(url, destination, sha1, size, listener);
                return;
            } catch (CancelledException | PermanentFailure e) {
                throw e;
            } catch (IOException e) {
                last = e;
                log.warn("Download attempt {} of {} failed for {}: {}", attempt, ATTEMPTS, url, e.toString());
                if (attempt < ATTEMPTS) {
                    pause(1000L * attempt, listener);
                }
            }
        }
        throw last;
    }

    private static void fetchOnce(String url, File destination, String sha1, long size, ProgressListener listener)
            throws IOException {
        FileUtil.createFolder(destination.getParentFile());
        File partial = new File(destination.getParentFile(), destination.getName() + ".part");

        HttpGet request = new HttpGet(url);
        request.setConfig(REQUEST_CONFIG);
        request.addHeader(HttpHeaders.USER_AGENT, LauncherUserAgent.USER_AGENT);
        try {
            EHttpClient.getGlobalClient().execute(request, response -> {
                int code = response.getCode();
                if (code >= 400) {
                    // these will not get better by asking again - except a timeout or a
                    // rate limit, which is exactly what asking again a moment later is for
                    String message = "the server answered " + code + " for " + url;
                    if (code < 500 && code != 408 && code != 429) {
                        throw new PermanentFailure(message);
                    }
                    throw new IOException(message);
                }
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new IOException("no content received for " + url);
                }
                long announced = entity.getContentLength();
                long total = announced > 0 ? announced : size;

                MessageDigest digest = sha1Digest();
                long done = 0;
                if (listener != null) {
                    listener.onBytes(0, total);
                }
                try (InputStream in = entity.getContent();
                     OutputStream out = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        checkCancelled(listener);
                        out.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        done += read;
                        if (listener != null) {
                            listener.onBytes(done, total);
                        }
                    }
                }
                if (announced > 0 && done != announced) {
                    throw new IOException("download stopped early: got " + done + " of " + announced + " bytes");
                }
                if (StringUtils.isNotEmpty(sha1) && !sha1.equalsIgnoreCase(hex(digest.digest()))) {
                    throw new IOException(destination.getName() + " does not match its published SHA-1 hash");
                }
                return null;
            });
            Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            partial.delete();
        }
    }

    /**
     * A download failure that retrying cannot fix, such as a file that is not there.
     */
    private static final class PermanentFailure extends IOException {
        PermanentFailure(String message) {
            super(message);
        }
    }

    private static void checkCancelled(ProgressListener listener) throws CancelledException {
        if (listener != null && listener.isCancelled()) {
            throw new CancelledException();
        }
    }

    private static void stage(ProgressListener listener, Stage stage) {
        if (listener != null) {
            listener.onStage(stage);
        }
    }

    /**
     * Waits before a retry, but notices a cancel within a fraction of a second rather
     * than making the user sit out the whole delay.
     */
    private static void pause(long millis, ProgressListener listener) throws CancelledException {
        long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until) {
            checkCancelled(listener);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancelledException();
            }
        }
    }

    private static MessageDigest sha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by the Java platform", e);
        }
    }

    private static String hex(byte[] hash) {
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
     * @return the SHA-1 of a file on disk, for checking a copy the user fetched by hand
     */
    public static String sha1(File file) throws IOException {
        MessageDigest digest = sha1Digest();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
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
     * <p>
     * A file whose author does not allow third-party downloads does not stop the install:
     * everything else goes in, and the file comes back in {@link Result#getSkipped()} for
     * the user to fetch by hand. Throwing the whole pack away over one such mod - which
     * is what used to happen - left the user with nothing at all, and nothing to act on.
     */
    public static Result importCurseForge(File zipFile, InstanceManager manager, ProgressListener listener)
            throws IOException {
        String apiKey = CurseForgeProvider.getApiKey();
        if (StringUtils.isEmpty(apiKey)) {
            throw new IOException("a CurseForge API key is needed to import a CurseForge modpack");
        }
        stage(listener, Stage.RESOLVING);

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

        // project id per file id, in the manifest's order
        Map<Long, Long> projectOf = new java.util.LinkedHashMap<>();
        JsonArray entries = manifest.has("files") ? manifest.getAsJsonArray("files") : new JsonArray();
        for (JsonElement e : entries) {
            JsonObject entry = e.getAsJsonObject();
            projectOf.put(entry.get("fileID").getAsLong(), entry.get("projectID").getAsLong());
        }

        // Both lookups happen before anything is created, so a CurseForge that cannot be
        // reached leaves no empty instance behind.
        Map<Long, CurseForgeApi.ModFile> files = new HashMap<>();
        for (CurseForgeApi.ModFile file : CurseForgeApi.getFiles(apiKey, new ArrayList<>(projectOf.keySet()))) {
            files.put(file.id, file);
        }
        Map<Long, CurseForgeApi.Mod> projects = new HashMap<>();
        try {
            for (CurseForgeApi.Mod mod : CurseForgeApi.getMods(apiKey,
                    new ArrayList<>(new LinkedHashSet<>(projectOf.values())))) {
                projects.put(mod.id, mod);
            }
        } catch (IOException e) {
            // only needed for nicer names and for sorting resource packs out of mods/ -
            // worth a warning, not worth failing the install over
            log.warn("Could not look up the projects of {}; everything goes into mods/", name, e);
        }

        Instance instance = manager.create(name, versionId);
        File gameDir = instance.getGameDir();
        List<SkippedFile> skipped = new ArrayList<>();

        try {
            List<CurseForgeApi.ModFile> downloadable = new ArrayList<>();
            for (Entry<Long, Long> entry : projectOf.entrySet()) {
                long fileId = entry.getKey();
                long projectId = entry.getValue();
                CurseForgeApi.ModFile file = files.get(fileId);
                CurseForgeApi.Mod project = projects.get(projectId);
                if (file != null && StringUtils.isNotEmpty(file.downloadUrl)) {
                    downloadable.add(file);
                    continue;
                }
                String fileName = file != null ? file.fileName : null;
                String projectName = project != null && StringUtils.isNotEmpty(project.name)
                        ? project.name
                        : fileName != null ? fileName : "CurseForge #" + projectId;
                String page = project != null && project.links != null
                        && StringUtils.isNotEmpty(project.links.websiteUrl)
                        ? StringUtils.removeEnd(project.links.websiteUrl, "/") + "/files/" + fileId
                        : null;
                skipped.add(new SkippedFile(projectName, fileName, page,
                        file != null ? file.hash(1) : null, folderFor(gameDir, project)));
                log.info("Skipping {} ({}): CurseForge will not serve it to third-party apps", projectName, fileName);
            }

            stage(listener, Stage.DOWNLOADING_FILES);
            for (int i = 0; i < downloadable.size(); i++) {
                CurseForgeApi.ModFile file = downloadable.get(i);
                if (listener != null) {
                    listener.onStep(file.fileName, i + 1, downloadable.size());
                }
                File folder = folderFor(gameDir, projects.get(file.modId));
                fetch(file.downloadUrl, target(folder, file.fileName),
                        file.hash(1), // CurseForge algo 1 = sha1
                        file.fileLength, listener);
            }

            stage(listener, Stage.EXTRACTING);
            String overrides = manifest.has("overrides") ? manifest.get("overrides").getAsString() : "overrides";
            try (ZipFile zip = new ZipFile(zipFile)) {
                extractPrefixed(zip, overrides.endsWith("/") ? overrides : overrides + "/", gameDir);
            }
        } catch (IOException e) {
            // a half-installed modpack is worse than none - the user can just try again
            FileUtil.deleteDirectory(instance.getFolder());
            throw e;
        }
        return new Result(instance, skipped);
    }

    /**
     * Where a pack's file goes. Packs list resource packs and shaders right alongside the
     * mods, and putting everything into mods/ quietly left those switched off.
     */
    private static File folderFor(File gameDir, CurseForgeApi.Mod project) {
        ContentType type = ContentType.MOD;
        if (project != null) {
            if (project.classId == CurseForgeApi.classIdOf(ContentType.RESOURCE_PACK)) {
                type = ContentType.RESOURCE_PACK;
            } else if (project.classId == CurseForgeApi.classIdOf(ContentType.SHADER)) {
                type = ContentType.SHADER;
            }
        }
        return new File(gameDir, type.getFolder());
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
     * Where one downloaded file goes inside a folder, refusing a name that would escape it.
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
