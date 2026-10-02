package net.legacylauncher.modpack;

import net.legacylauncher.instance.Instance;
import net.legacylauncher.instance.InstanceManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An update replaces what the pack installed and nothing else. Getting this wrong deletes
 * somebody's world or the mods they added themselves, so it is pinned down here.
 */
class PackUpdateTest {

    private static File zip(File dir, String name, String... pathsAndContents) throws IOException {
        File file = new File(dir, name);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < pathsAndContents.length; i += 2) {
                out.putNextEntry(new ZipEntry("overrides/" + pathsAndContents[i]));
                out.write(pathsAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return file;
    }

    private static PackPlan plan(File zip, String version) {
        PackPlan plan = new PackPlan("Test pack", version, "1.20.1", null, null);
        plan.getOverlays().add(new PackPlan.Overlay(zip, "overrides/", null));
        return plan;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File file, String text) throws IOException {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("an update swaps the pack's files and keeps the player's")
    void updateKeepsPlayerFiles() throws Exception {
        File root = Files.createTempDirectory("pack-update").toFile();
        File zips = Files.createTempDirectory("pack-zips").toFile();
        InstanceManager manager = new InstanceManager(root);
        Instance instance = manager.create("Test", "1.20.1");
        File game = instance.getGameDir();
        ModpackOrigin origin = new ModpackOrigin("modrinth", "abc", "Test pack", "v1", "1.0", null, null);

        PackInstaller.update(instance, plan(zip(zips, "v1.zip",
                "mods/a.jar", "a1",
                "mods/b.jar", "b1",
                "config/x.cfg", "x1",
                "options.txt", "pack-options",
                "saves/w/level.dat", "pack-world"), "1.0"), origin, "1.20.1", manager, null);

        assertEquals("a1", read(new File(game, "mods/a.jar")));
        assertEquals("pack-options", read(new File(game, "options.txt")));

        // the player plays for a while
        write(new File(game, "mods/mine.jar"), "mine");
        write(new File(game, "options.txt"), "my-options");
        write(new File(game, "saves/w/level.dat"), "my-world");

        PackInstaller.update(instance, plan(zip(zips, "v2.zip",
                "mods/a.jar", "a2",
                "mods/c.jar", "c2",
                "config/x.cfg", "x2",
                "options.txt", "pack-options-2",
                "saves/w/level.dat", "pack-world-2"), "2.0"),
                origin.withVersion("v2", "2.0"), "1.20.1-forge-47.2.0", manager, null);

        assertEquals("a2", read(new File(game, "mods/a.jar")), "the pack's mod is replaced");
        assertFalse(new File(game, "mods/b.jar").exists(), "a mod the new version dropped is removed");
        assertEquals("c2", read(new File(game, "mods/c.jar")), "a mod the new version added is installed");
        assertEquals("x2", read(new File(game, "config/x.cfg")), "the pack's config is replaced");
        assertEquals("mine", read(new File(game, "mods/mine.jar")), "a mod the player added stays");
        assertEquals("my-options", read(new File(game, "options.txt")), "the player's settings stay");
        assertEquals("my-world", read(new File(game, "saves/w/level.dat")), "the player's world stays");
        assertEquals("1.20.1-forge-47.2.0", instance.getVersionId());
        assertEquals("2.0", instance.getModpack().getVersionName());
        assertFalse(new File(instance.getFolder(), ".pack-update").exists(), "no staging folder is left");

        // and it survives a reload from disk
        Instance reloaded = null;
        for (Instance candidate : manager.refresh()) {
            if (candidate.getId().equals(instance.getId())) {
                reloaded = candidate;
            }
        }
        assertEquals("v2", reloaded.getModpack().getVersionId());
    }

    @Test
    @DisplayName("a failed update leaves the instance exactly as it was")
    void failedUpdateChangesNothing() throws Exception {
        File root = Files.createTempDirectory("pack-update").toFile();
        File zips = Files.createTempDirectory("pack-zips").toFile();
        InstanceManager manager = new InstanceManager(root);
        Instance instance = manager.create("Test", "1.20.1");
        File game = instance.getGameDir();
        ModpackOrigin origin = new ModpackOrigin("modrinth", "abc", "Test pack", "v1", "1.0", null, null);
        PackInstaller.update(instance, plan(zip(zips, "v1.zip", "mods/a.jar", "a1"), "1.0"),
                origin, "1.20.1", manager, null);

        PackPlan broken = plan(zip(zips, "v2.zip", "mods/a.jar", "a2"), "2.0");
        broken.getDownloads().add(PackPlan.Download.file("file:///nonexistent/nothing.jar", "mods/z.jar",
                null, null, -1));
        assertThrows(IOException.class, () -> PackInstaller.update(instance, broken,
                origin.withVersion("v2", "2.0"), "1.20.1-forge", manager, null));

        assertEquals("a1", read(new File(game, "mods/a.jar")));
        assertFalse(new File(game, "mods/z.jar").exists());
        assertEquals("1.20.1", instance.getVersionId());
        assertEquals("1.0", instance.getModpack().getVersionName());
        assertFalse(new File(instance.getFolder(), ".pack-update").exists());
    }

    @Test
    @DisplayName("pack paths cannot climb out of the instance")
    void pathsStayInside() throws Exception {
        File root = Files.createTempDirectory("pack-paths").toFile();
        assertThrows(IOException.class, () -> PackPaths.resolve(root, "../evil.jar"));
        assertThrows(IOException.class, () -> PackPaths.resolve(root, "mods/../../evil.jar"));
        assertTrue(PackPaths.resolve(root, "./config/x.toml").getPath().endsWith("x.toml"));
        assertEquals("config/a.toml", PackPaths.join("./config", "a.toml"));
        assertEquals("mods/a.jar", PackPaths.join("", "/mods/a.jar"));
    }
}
