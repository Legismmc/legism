package net.legacylauncher.instance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceManagerTest {

    @TempDir
    Path root;

    private InstanceManager manager;

    @BeforeEach
    void setUp() {
        manager = new InstanceManager(root.toFile());
    }

    @Test
    void createsAnInstanceWithItsOwnGameDirectory() throws IOException {
        Instance instance = manager.create("Test Pack", "1.20.1");

        assertEquals("test-pack", instance.getId());
        assertEquals("Test Pack", instance.getName());
        assertEquals("1.20.1", instance.getVersionId());
        assertTrue(instance.getGameDir().isDirectory());
        assertTrue(instance.getDescriptorFile().isFile());
        assertEquals(1, manager.refresh().size());
    }

    @Test
    void readsInstancesBackFromDisk() throws IOException {
        manager.create("Alpha", "1.20.1");
        manager.create("Beta", "1.7.10");

        List<Instance> found = new InstanceManager(root.toFile()).refresh();

        assertEquals(2, found.size());
        assertEquals("Alpha", found.get(0).getName());
        assertEquals("Beta", found.get(1).getName());
    }

    @Test
    void doesNotReuseAFolderName() throws IOException {
        Instance first = manager.create("Same Name", "1.20.1");
        Instance second = manager.create("Same Name", "1.20.1");

        assertNotEquals(first.getId(), second.getId());
        assertEquals("same-name", first.getId());
        assertEquals("same-name-2", second.getId());
    }

    @Test
    void fallsBackToADefaultIdWhenTheNameHasNoLatinLetters() {
        assertEquals("instance", InstanceManager.toId("Сборка"));
        assertEquals("my-pack", InstanceManager.toId("My  Pack!"));
        assertEquals("pack-1", InstanceManager.toId("Pack 1"));
    }

    @Test
    void rejectsAnEmptyNameOrVersion() {
        assertThrows(IOException.class, () -> manager.create("  ", "1.20.1"));
        assertThrows(IOException.class, () -> manager.create("Fine", " "));
    }

    @Test
    void groupsInstances() throws IOException {
        Instance instance = manager.create("Grouped", "1.20.1");
        assertEquals("", instance.getGroup());

        manager.setGroup(instance, "Мои сборки");

        assertEquals("Мои сборки", manager.refresh().get(0).getGroup());
        assertEquals(List.of("Мои сборки"), manager.getGroups());

        manager.setGroup(manager.refresh().get(0), "   ");
        assertEquals("", manager.refresh().get(0).getGroup());
        assertTrue(manager.getGroups().isEmpty());
    }

    @Test
    void changesTheIcon() throws IOException {
        Instance instance = manager.create("Iconic", "1.20.1");
        assertEquals(null, instance.getIcon());

        manager.setIcon(instance, "diamond");

        assertEquals("diamond", manager.refresh().get(0).getIcon());
        // a fresh reader off disk should see it too, not just the in-memory instance
        assertEquals("diamond", new InstanceManager(root.toFile()).refresh().get(0).getIcon());
    }

    @Test
    void duplicatesTheGameDirectoryToo() throws IOException {
        Instance source = manager.create("Original", "1.20.1");
        File mods = new File(source.getGameDir(), "mods");
        assertTrue(mods.mkdirs());
        Files.write(new File(mods, "sodium.jar").toPath(), "not really a jar".getBytes(StandardCharsets.UTF_8));
        manager.setGroup(source, "Packs");
        manager.setIcon(source, "emerald");

        Instance copy = manager.duplicate(manager.refresh().get(0), "Copy of it");

        assertEquals("copy-of-it", copy.getId());
        assertEquals("Packs", copy.getGroup());
        assertEquals("emerald", copy.getIcon());
        assertEquals("1.20.1", copy.getVersionId());
        File copiedMod = new File(copy.getGameDir(), "mods/sodium.jar");
        assertTrue(copiedMod.isFile(), "the copy should carry the mods over");
        assertEquals("not really a jar",
                new String(Files.readAllBytes(copiedMod.toPath()), StandardCharsets.UTF_8));
        assertEquals(2, manager.refresh().size());
    }

    @Test
    void exportsEverythingIntoAZip() throws IOException {
        Instance instance = manager.create("Exported", "1.20.1");
        File mods = new File(instance.getGameDir(), "mods");
        assertTrue(mods.mkdirs());
        Files.write(new File(mods, "a.jar").toPath(), new byte[]{1, 2, 3});

        File zip = root.resolve("out.zip").toFile();
        manager.export(instance, zip);

        assertTrue(zip.isFile());
        boolean hasDescriptor = false;
        boolean hasMod = false;
        try (ZipFile file = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                hasDescriptor |= name.equals(Instance.DESCRIPTOR);
                hasMod |= name.equals(Instance.GAME_SUBFOLDER + "/mods/a.jar");
            }
        }
        assertTrue(hasDescriptor, "the descriptor should be in the archive");
        assertTrue(hasMod, "the mods should be in the archive");
    }

    @Test
    void deletesTheWholeFolder() throws IOException {
        Instance instance = manager.create("Doomed", "1.20.1");
        File world = new File(instance.getGameDir(), "saves/world");
        assertTrue(world.mkdirs());

        manager.delete(instance);

        assertFalse(instance.getFolder().exists());
        assertTrue(manager.refresh().isEmpty());
    }

    @Test
    void accumulatesPlayTimeAcrossSessions() throws IOException {
        Instance instance = manager.create("Played", "1.20.1");
        assertEquals(0L, instance.getTotalPlayTime());
        assertEquals(0L, instance.getLastPlayed());

        manager.startSession(instance);
        assertEquals(instance, manager.getRunning());
        assertTrue(instance.getLastPlayed() > 0L);
        manager.finishSession();

        assertEquals(null, manager.getRunning());
        // the descriptor keeps the totals, so they survive a restart
        Instance reloaded = new InstanceManager(root.toFile()).refresh().get(0);
        assertTrue(reloaded.getLastPlayed() > 0L);
    }

    @Test
    void ignoresFoldersWithoutADescriptor() throws IOException {
        manager.create("Real", "1.20.1");
        assertTrue(new File(root.toFile(), "junk").mkdirs());

        assertEquals(1, manager.refresh().size());
    }
}
