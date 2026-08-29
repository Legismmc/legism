package net.legacylauncher.modrinth;

import java.io.IOException;
import java.util.List;

/**
 * A library the launcher can install content from.
 * <p>
 * Each provider keeps its own idea of projects, versions and dependencies to itself and
 * hands the browser the neutral {@link ContentProject} and {@link ContentFile} instead, so
 * the screen does not have to know which library it is talking to.
 * <p>
 * Every method blocks, so callers must stay off the Swing thread.
 */
public interface ContentProvider {

    /**
     * Stable identifier, used to remember which library the user last picked.
     */
    String getId();

    /**
     * Name shown in the library picker.
     */
    String getDisplayName();

    /**
     * Whether the provider can be used right now. CurseForge, for one, needs an API key
     * before it will answer at all.
     */
    boolean isAvailable();

    /**
     * Why {@link #isAvailable()} is false, ready to show to the user; {@code null} when
     * the provider is fine.
     */
    String getUnavailableReason();

    /**
     * Whether this provider offers the given kind of content at all.
     */
    boolean supports(ContentType type);

    /**
     * Searches the library.
     *
     * @param sort one of the ids returned by {@link #getSortOptions()}
     */
    ContentSearchResult search(ContentType type, String query, String gameVersion,
                               String loader, String sort, int offset, int limit) throws IOException;

    /**
     * Works out everything that has to be downloaded to install a project - the file
     * itself first, then whatever it requires.
     *
     * @return an empty list when nothing fits the given game version and loader
     */
    List<ContentFile> plan(ContentType type, String projectId, String gameVersion,
                           String loader, boolean withDependencies) throws IOException;

    /**
     * How deep into the results the library will let anyone page, counted as the largest
     * {@code offset + limit} it will still answer.
     * <p>
     * Only matters once the browser offers numbered pages: with a "load more" button
     * nobody ever walked far enough to hit a wall, but a page list happily points at the
     * five-hundredth page, and CurseForge answers 400 rather than an empty list once
     * {@code offset + limit} passes 10000. Providers with no such wall return
     * {@link Integer#MAX_VALUE}.
     */
    int getMaxSearchDepth();

    /**
     * Sort orders the library offers, in the order they should be listed. The first is
     * used as the default.
     */
    List<SortOption> getSortOptions();

    /**
     * The Minecraft versions worth offering as a filter, newest first.
     */
    List<String> listGameVersions() throws IOException;

    /**
     * One sort order: the value the library expects, and the key its caption lives under.
     */
    class SortOption {
        private final String id;
        private final String labelKey;

        public SortOption(String id, String labelKey) {
            this.id = id;
            this.labelKey = labelKey;
        }

        public String getId() {
            return id;
        }

        public String getLabelKey() {
            return labelKey;
        }
    }
}
