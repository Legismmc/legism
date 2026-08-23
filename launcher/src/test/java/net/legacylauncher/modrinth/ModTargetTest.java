package net.legacylauncher.modrinth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModTargetTest {

    @Test
    void extractsPlainReleases() {
        assertEquals("1.20.1", ModTarget.extractGameVersion("1.20.1"));
        assertEquals("1.20", ModTarget.extractGameVersion("1.20"));
        assertEquals("1.7.10", ModTarget.extractGameVersion("1.7.10"));
    }

    @Test
    void extractsFromModdedVersionIds() {
        assertEquals("1.20.1", ModTarget.extractGameVersion("1.20.1-forge-47.2.20"));
        assertEquals("1.20.4", ModTarget.extractGameVersion("fabric-loader-0.15.7-1.20.4"));
        assertEquals("1.12.2", ModTarget.extractGameVersion("1.12.2-Forge-14.23.5.2860"));
        assertEquals("1.20.1", ModTarget.extractGameVersion("quilt-loader-0.23.1-1.20.1"));
    }

    @Test
    void extractsSnapshots() {
        assertEquals("24w14a", ModTarget.extractGameVersion("24w14a"));
        assertEquals("24w09a", ModTarget.extractGameVersion("fabric-loader-0.15.7-24w09a"));
    }

    @Test
    void returnsNullWhenThereIsNothingToExtract() {
        assertNull(ModTarget.extractGameVersion(null));
        assertNull(ModTarget.extractGameVersion(""));
        assertNull(ModTarget.extractGameVersion("rd-132211"));
    }

    @Test
    void detectsLoaders() {
        assertEquals(ModLoader.FORGE, ModLoader.detect("1.20.1-forge-47.2.20"));
        assertEquals(ModLoader.FABRIC, ModLoader.detect("fabric-loader-0.15.7-1.20.4"));
        assertEquals(ModLoader.QUILT, ModLoader.detect("quilt-loader-0.23.1-1.20.1"));
        assertEquals(ModLoader.FORGE, ModLoader.detect("Forge-1.20.1"));
        assertEquals(ModLoader.FABRIC, ModLoader.detect("Fabric-1.20.1"));
    }

    @Test
    void prefersNeoForgeOverForge() {
        // "neoforge" contains "forge", so the order the two are tested in matters
        assertEquals(ModLoader.NEOFORGE, ModLoader.detect("neoforge-20.4.190"));
        assertEquals(ModLoader.NEOFORGE, ModLoader.detect("1.20.4-neoforge"));
    }

    @Test
    void detectsNoLoaderForVanilla() {
        assertNull(ModLoader.detect("1.20.1"));
        assertNull(ModLoader.detect(null));
        assertNull(ModLoader.detect(""));
    }
}
