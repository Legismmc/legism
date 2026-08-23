package net.legacylauncher.modrinth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The content libraries the launcher knows about.
 * <p>
 * Modrinth comes first because it works out of the box; CurseForge needs a key of the
 * user's own and is listed regardless, so the browser can explain why it is unavailable
 * rather than pretending it does not exist.
 */
public final class ContentProviders {
    private static final List<ContentProvider> ALL = Collections.unmodifiableList(
            new ArrayList<ContentProvider>() {{
                add(new ModrinthProvider());
                add(new CurseForgeProvider());
            }});

    private ContentProviders() {
    }

    public static List<ContentProvider> all() {
        return ALL;
    }

    public static ContentProvider byId(String id) {
        for (ContentProvider provider : ALL) {
            if (provider.getId().equals(id)) {
                return provider;
            }
        }
        return ALL.get(0);
    }

    public static ContentProvider getDefault() {
        return ALL.get(0);
    }
}
