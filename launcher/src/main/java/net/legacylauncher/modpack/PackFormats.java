package net.legacylauncher.modpack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.modrinth.ContentType;
import net.legacylauncher.modrinth.CurseForgeApi;
import net.legacylauncher.modrinth.CurseForgeProvider;
import net.legacylauncher.modrinth.ModLoader;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads the two pack archives in common use - Modrinth's {@code .mrpack} and CurseForge's
 * zip with a {@code manifest.json} - into a {@link PackPlan}. Both are an index of files
 * to download plus a folder of files to copy in from the archive itself.
 */
@Slf4j
public final class PackFormats {
    private PackFormats() {
    }

    /**
     * @throws IOException when the file is neither, or cannot be read
     */
    public static PackPlan read(File file, ModpackImporter.ProgressListener listener) throws IOException {
        switch (ModpackImporter.detectFormat(file)) {
            case MRPACK:
                return readMrpack(file);
            case CURSEFORGE:
                return readCurseForge(file, listener);
            default:
                throw new IOException("unrecognised modpack format");
        }
    }

    private static JsonObject readJson(File zipFile, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                throw new IOException("not a modpack: no " + entryName);
            }
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    // ---------------------------------------------------------------- .mrpack

    private static PackPlan readMrpack(File file) throws IOException {
        JsonObject index = readJson(file, "modrinth.index.json");
        JsonObject dependencies = index.has("dependencies") ? index.getAsJsonObject("dependencies") : null;
        if (dependencies == null || !dependencies.has("minecraft")) {
            throw new IOException("modpack does not declare a Minecraft version");
        }

        ModLoader loader = null;
        String loaderVersion = null;
        for (Entry<String, JsonElement> entry : dependencies.entrySet()) {
            ModLoader detected = ModLoader.detect(entry.getKey());
            if (detected != null) {
                loader = detected;
                loaderVersion = entry.getValue().getAsString();
                break;
            }
        }

        String name = string(index, "name");
        PackPlan plan = new PackPlan(name == null ? file.getName() : name, string(index, "versionId"),
                dependencies.get("minecraft").getAsString(), loader, loaderVersion);

        JsonArray files = index.has("files") ? index.getAsJsonArray("files") : new JsonArray();
        for (JsonElement element : files) {
            JsonObject entry = element.getAsJsonObject();
            if (isUnsupportedForClient(entry)) {
                continue;
            }
            List<String> urls = new ArrayList<>();
            if (entry.has("downloads")) {
                for (JsonElement url : entry.getAsJsonArray("downloads")) {
                    urls.add(url.getAsString());
                }
            }
            String sha1 = entry.has("hashes") ? string(entry.getAsJsonObject("hashes"), "sha1") : null;
            long size = entry.has("fileSize") ? entry.get("fileSize").getAsLong() : -1;
            plan.getDownloads().add(PackPlan.Download.file(urls, entry.get("path").getAsString(),
                    sha1 == null ? null : "SHA-1", sha1, size));
        }
        plan.getOverlays().add(new PackPlan.Overlay(file, "overrides/", null));
        plan.getOverlays().add(new PackPlan.Overlay(file, "client-overrides/", null));
        return plan;
    }

    private static boolean isUnsupportedForClient(JsonObject file) {
        if (!file.has("env")) {
            return false;
        }
        JsonObject env = file.getAsJsonObject("env");
        return env.has("client") && "unsupported".equals(env.get("client").getAsString());
    }

    // ---------------------------------------------------------------- CurseForge

    /**
     * A CurseForge pack names every mod by project and file id only, so each has to be
     * resolved through CurseForge's API - in batches, since one request per mod is what
     * used to make big packs fail at random.
     * <p>
     * A file whose author does not allow third-party downloads does not stop the install:
     * it is listed in {@link PackPlan#getSkipped()} for the user to fetch by hand.
     */
    private static PackPlan readCurseForge(File file, ModpackImporter.ProgressListener listener) throws IOException {
        String apiKey = CurseForgeProvider.getApiKey();
        if (StringUtils.isEmpty(apiKey)) {
            throw new IOException("a CurseForge API key is needed to import a CurseForge modpack");
        }
        if (listener != null) {
            listener.onStage(ModpackImporter.Stage.RESOLVING);
        }

        JsonObject manifest = readJson(file, "manifest.json");
        JsonObject minecraft = manifest.has("minecraft") ? manifest.getAsJsonObject("minecraft") : null;
        if (minecraft == null || !minecraft.has("version")) {
            throw new IOException("modpack does not declare a Minecraft version");
        }

        String loaderId = curseForgeLoaderId(minecraft);
        ModLoader loader = loaderId == null ? null : ModLoader.detect(loaderId);
        String loaderVersion = null;
        if (loaderId != null && loaderId.indexOf('-') > 0) {
            loaderVersion = loaderId.substring(loaderId.indexOf('-') + 1);
        }
        String name = string(manifest, "name");
        PackPlan plan = new PackPlan(name == null ? file.getName() : name, string(manifest, "version"),
                minecraft.get("version").getAsString(), loader, loaderVersion);

        Map<Long, Long> projectOf = new LinkedHashMap<>();
        JsonArray entries = manifest.has("files") ? manifest.getAsJsonArray("files") : new JsonArray();
        for (JsonElement e : entries) {
            JsonObject entry = e.getAsJsonObject();
            projectOf.put(entry.get("fileID").getAsLong(), entry.get("projectID").getAsLong());
        }
        addCurseForgeFiles(plan, apiKey, projectOf);

        String overrides = manifest.has("overrides") ? manifest.get("overrides").getAsString() : "overrides";
        plan.getOverlays().add(new PackPlan.Overlay(file, overrides.endsWith("/") ? overrides : overrides + "/", null));
        return plan;
    }

    /**
     * Resolves CurseForge files by id into downloads - or, for files their authors keep
     * to CurseForge's own app, into entries for the user to fetch by hand. Also used by the
     * other libraries, which sometimes point at CurseForge for a mod instead of hosting it.
     *
     * @param projectOf project id per file id
     */
    public static void addCurseForgeFiles(PackPlan plan, String apiKey, Map<Long, Long> projectOf) throws IOException {
        if (projectOf.isEmpty()) {
            return;
        }
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
            log.warn("Could not look up CurseForge projects; everything goes into mods/", e);
        }

        for (Entry<Long, Long> entry : projectOf.entrySet()) {
            long fileId = entry.getKey();
            long projectId = entry.getValue();
            CurseForgeApi.ModFile file = files.get(fileId);
            CurseForgeApi.Mod project = projects.get(projectId);
            String folder = curseForgeFolder(project);
            if (file != null && StringUtils.isNotEmpty(file.downloadUrl)) {
                String sha1 = file.hash(1); // CurseForge algo 1 = sha1
                plan.getDownloads().add(PackPlan.Download.file(file.downloadUrl,
                        PackPaths.join(folder, file.fileName), sha1 == null ? null : "SHA-1", sha1, file.fileLength));
                continue;
            }
            String fileName = file != null ? file.fileName : null;
            String projectName = project != null && StringUtils.isNotEmpty(project.name)
                    ? project.name
                    : fileName != null ? fileName : "CurseForge #" + projectId;
            String page = project != null && project.links != null && StringUtils.isNotEmpty(project.links.websiteUrl)
                    ? StringUtils.removeEnd(project.links.websiteUrl, "/") + "/files/" + fileId
                    : null;
            plan.getSkipped().add(new PackPlan.Skipped(projectName, fileName, page,
                    file != null ? file.hash(1) : null, folder));
            log.info("Skipping {} ({}): CurseForge will not serve it to third-party apps", projectName, fileName);
        }
    }

    /**
     * Where a pack's file goes. Packs list resource packs and shaders right alongside the
     * mods, and putting everything into mods/ quietly left those switched off.
     */
    private static String curseForgeFolder(CurseForgeApi.Mod project) {
        if (project != null) {
            if (project.classId == CurseForgeApi.classIdOf(ContentType.RESOURCE_PACK)) {
                return ContentType.RESOURCE_PACK.getFolder();
            }
            if (project.classId == CurseForgeApi.classIdOf(ContentType.SHADER)) {
                return ContentType.SHADER.getFolder();
            }
        }
        return ContentType.MOD.getFolder();
    }

    /**
     * @return the pack's primary loader id such as {@code forge-47.2.0}, or the first one
     * when none is flagged primary; {@code null} for a vanilla pack
     */
    private static String curseForgeLoaderId(JsonObject minecraft) {
        if (!minecraft.has("modLoaders")) {
            return null;
        }
        String first = null;
        for (JsonElement e : minecraft.getAsJsonArray("modLoaders")) {
            JsonObject loader = e.getAsJsonObject();
            String id = string(loader, "id");
            if (id == null || ModLoader.detect(id) == null) {
                continue;
            }
            if (loader.has("primary") && loader.get("primary").getAsBoolean()) {
                return id;
            }
            if (first == null) {
                first = id;
            }
        }
        return first;
    }

    static List<String> list(String... values) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, values);
        return result;
    }
}
