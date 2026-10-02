package net.legacylauncher.modpack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The libraries modpacks can be installed from, in the order the catalog lists them.
 */
public final class ModpackSources {
    private static final List<ModpackSource> ALL = Collections.unmodifiableList(Arrays.<ModpackSource>asList(
            new ModrinthModpackSource(),
            new CurseForgeModpackSource(),
            new FtbModpackSource(),
            new TechnicModpackSource(),
            new AtLauncherModpackSource()
    ));

    private ModpackSources() {
    }

    public static List<ModpackSource> all() {
        return ALL;
    }

    /**
     * @return the source with this id, or {@code null} when there is none - an instance
     * installed by a newer launcher, say
     */
    public static ModpackSource byId(String id) {
        for (ModpackSource source : ALL) {
            if (source.getId().equals(id)) {
                return source;
            }
        }
        return null;
    }
}
