package net.legacylauncher.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The updater finds its download by matching asset names, which means a release that
 * renames its files silently breaks updating for everyone already installed - and nobody
 * finds out until the next release, because the broken half is in the copy people already
 * have. These are the names the workflow actually publishes.
 */
class SelfUpdaterAssetTest {

    private static final String[] PUBLISHED = {
            "Legism_windows_installer.exe",
            "Legism_windows_portable.zip",
            "Legism_linux.tar.gz",
            "Legism_macos_apple_silicon.dmg",
            "Legism_macos_intel.dmg"
    };

    private static SelfUpdateChecker.LatestRelease release() throws Exception {
        Constructor<SelfUpdateChecker.Asset> asset =
                SelfUpdateChecker.Asset.class.getDeclaredConstructor(String.class, String.class, long.class);
        asset.setAccessible(true);

        List<SelfUpdateChecker.Asset> assets = new ArrayList<>();
        for (String name : PUBLISHED) {
            assets.add(asset.newInstance(name, "https://example.invalid/" + name, 1024L));
        }

        Constructor<SelfUpdateChecker.LatestRelease> release =
                SelfUpdateChecker.LatestRelease.class.getDeclaredConstructor(String.class, String.class, List.class);
        release.setAccessible(true);
        return release.newInstance("v9.9.9", "https://example.invalid/release", assets);
    }

    /**
     * Runs the same name matching the updater uses, for a platform other than the one the
     * test happens to be running on.
     */
    private static String pick(String contains, String suffix) throws Exception {
        for (SelfUpdateChecker.Asset candidate : release().getAssets()) {
            String name = candidate.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains(contains) && name.endsWith(suffix)) {
                return candidate.getName();
            }
        }
        return null;
    }

    @Test
    @DisplayName("every platform still finds a file in the published set")
    void everyPlatformResolves() throws Exception {
        assertEquals("Legism_windows_installer.exe", pick("windows", ".exe"));
        assertEquals("Legism_windows_portable.zip", pick("windows", ".zip"));
        assertEquals("Legism_linux.tar.gz", pick("linux", ".tar.gz"));
        assertEquals("Legism_macos_apple_silicon.dmg", pick("apple_silicon", ".dmg"));
        assertEquals("Legism_macos_intel.dmg", pick("intel", ".dmg"));
    }

    @Test
    @DisplayName("the machine running this finds its own download")
    void thisPlatformResolves() throws Exception {
        SelfUpdater.Plan plan = SelfUpdater.planFor(release());
        assertNotNull(plan, "no build matched this platform out of: " + String.join(", ", PUBLISHED));
        assertNotNull(plan.getAsset().getUrl());
    }

    @Test
    @DisplayName("a release with no files falls back rather than picking nonsense")
    void emptyReleaseIsRefused() throws Exception {
        Constructor<SelfUpdateChecker.LatestRelease> release =
                SelfUpdateChecker.LatestRelease.class.getDeclaredConstructor(String.class, String.class, List.class);
        release.setAccessible(true);
        assertEquals(null, SelfUpdater.planFor(
                release.newInstance("v9.9.9", "https://example.invalid/", new ArrayList<>())));
    }
}
