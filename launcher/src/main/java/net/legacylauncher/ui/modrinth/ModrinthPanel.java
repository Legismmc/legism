package net.legacylauncher.ui.modrinth;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.modrinth.ContentType;
import net.legacylauncher.modrinth.InstalledMod;
import net.legacylauncher.modrinth.ModInstaller;
import net.legacylauncher.modrinth.ModLoader;
import net.legacylauncher.modrinth.ModTarget;
import net.legacylauncher.modrinth.ModrinthApi;
import net.legacylauncher.modrinth.ModrinthProject;
import net.legacylauncher.modrinth.ModrinthSearchResult;
import net.legacylauncher.modrinth.ModrinthVersion;
import net.legacylauncher.ui.MainPane;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.loc.LocalizableComponent;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;
import net.minecraft.launcher.updater.VersionSyncInfo;
import org.apache.commons.lang3.StringUtils;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Browses one kind of Modrinth content — mods, resource packs or shaders — and manages
 * what is already installed of that kind.
 * <p>
 * The same panel serves the standalone mods screen and each tab of the instance editor;
 * the difference is only whether it draws its own back button and target caption, and
 * which game directory the {@linkplain #targetSource target supplier} points at.
 */
@Slf4j
public class ModrinthPanel extends BackdropPanel implements LocalizableComponent {
    private static final int PAGE_SIZE = 20;

    private final MainPane pane;
    private final ContentType type;
    private final Supplier<ModTarget> targetSource;
    private final boolean standalone;

    private final JLabel targetLabel = new JLabel();
    private final JTextField searchField = new JTextField();
    private final JComboBox<String> gameVersionBox = new JComboBox<>();
    private final JComboBox<Object> loaderBox = new JComboBox<>();
    private final JComboBox<SortOption> sortBox = new JComboBox<>();
    private final JCheckBox dependenciesBox = new JCheckBox();
    private final JLabel statusLabel = new JLabel();

    private final JPanel resultsBox = new JPanel();
    private final JPanel installedBox = new JPanel();
    private final JTabbedPane tabs = new JTabbedPane();
    private final JButton loadMoreButton = new JButton();

    /**
     * The pieces whose text has to be redone when the user switches the launcher's
     * language, keyed by their {@link ModrinthStrings} key.
     */
    private final Map<String, JButton> localizedButtons = new LinkedHashMap<>();
    private final Map<String, JLabel> localizedLabels = new LinkedHashMap<>();

    /**
     * What the panel is currently browsing for. Rebuilt every time the screen is opened,
     * and adjusted in place when the user overrides the game version or loader.
     */
    private ModTarget target;
    private ModInstaller installer;

    /**
     * Bumped on every new search so that a slow response from a previous query cannot
     * overwrite the results of the current one.
     */
    private int searchGeneration;
    private int nextOffset;

    /**
     * Identifies the search currently being waited on, so an identical one is not fired
     * a second time. Cleared on the Swing thread when the request finishes.
     */
    private String inFlightSearch;

    /**
     * Browses mods for the version selected on the main screen, with its own back button.
     */
    public ModrinthPanel(MainPane pane) {
        this(pane, ContentType.MOD, () -> ModTarget.of(
                pane.defaultScene.loginForm.versions.getVersion(),
                LegacyLauncher.getInstance().getSettings()
        ), true);
    }

    public ModrinthPanel(MainPane pane, ContentType type, Supplier<ModTarget> targetSource, boolean standalone) {
        this.pane = pane;
        this.type = type;
        this.targetSource = targetSource;
        this.standalone = standalone;
        setVgap(SwingUtil.magnify(8));

        setNorth(buildHeader());
        setCenter(buildTabs());
        setSouth(buildStatusBar());
    }

    // ---------------------------------------------------------------- layout

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(SwingUtil.magnify(8), SwingUtil.magnify(8)));
        header.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout(SwingUtil.magnify(8), 0));
        top.setOpaque(false);

        if (standalone) {
            top.add(button("back", "arrow-left", e -> pane.openDefaultScene()), BorderLayout.WEST);
            targetLabel.setHorizontalAlignment(SwingConstants.CENTER);
            targetLabel.setFont(targetLabel.getFont().deriveFont(Font.BOLD));
            top.add(targetLabel, BorderLayout.CENTER);
        }

        top.add(button("open-folder", "folder-open", e -> openContentFolder()), BorderLayout.EAST);
        header.add(top, BorderLayout.NORTH);

        JPanel searchRow = new JPanel(new BorderLayout(SwingUtil.magnify(8), 0));
        searchRow.setOpaque(false);
        searchField.putClientProperty("JTextField.placeholderText", ModrinthStrings.get("search.hint"));
        searchField.addActionListener(e -> startSearch(true));
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(button("search", "search", e -> startSearch(true)), BorderLayout.EAST);

        header.add(searchRow, BorderLayout.CENTER);
        header.add(buildFilterRow(), BorderLayout.SOUTH);

        return header;
    }

    private JComponent buildFilterRow() {
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(6), 0));
        filters.setOpaque(false);

        filters.add(label("game-version"));
        // a fixed list only: typing a version by hand just produced empty result pages
        gameVersionBox.setEditable(false);
        gameVersionBox.setPrototypeDisplayValue("1.00.00");
        gameVersionBox.addActionListener(e -> onGameVersionChanged());
        filters.add(gameVersionBox);

        // only mods care which loader is installed; a resource pack or a shader does not
        if (type.isLoaderSpecific()) {
            filters.add(label("loader"));
            loaderBox.setModel(new DefaultComboBoxModel<>(new Object[]{
                    ModLoader.FABRIC, ModLoader.FORGE, ModLoader.NEOFORGE, ModLoader.QUILT
            }));
            loaderBox.addActionListener(e -> onLoaderChanged());
            filters.add(loaderBox);
        }

        filters.add(label("sort"));
        sortBox.setModel(new DefaultComboBoxModel<>(SortOption.values()));
        sortBox.addActionListener(e -> startSearch(true));
        filters.add(sortBox);

        if (type.isLoaderSpecific()) {
            dependenciesBox.setText(ModrinthStrings.get("dependencies"));
            dependenciesBox.setSelected(true);
            dependenciesBox.setOpaque(false);
            filters.add(dependenciesBox);
        }

        return filters;
    }

    /**
     * Creates a button whose caption is re-resolved on a language change.
     */
    private JButton button(String key, String icon, ActionListener action) {
        JButton button = new JButton(ModrinthStrings.get(key));
        button.setIcon(Images.getIcon16(icon));
        button.addActionListener(action);
        localizedButtons.put(key, button);
        return button;
    }

    /**
     * Creates a {@code "Caption:"} label whose caption is re-resolved on a language change.
     */
    private JLabel label(String key) {
        JLabel label = new JLabel(ModrinthStrings.get(key) + ":");
        localizedLabels.put(key, label);
        return label;
    }

    private JComponent buildTabs() {
        resultsBox.setLayout(new BoxLayout(resultsBox, BoxLayout.Y_AXIS));
        resultsBox.setOpaque(false);

        installedBox.setLayout(new BoxLayout(installedBox, BoxLayout.Y_AXIS));
        installedBox.setOpaque(false);

        loadMoreButton.setText(ModrinthStrings.get("load-more"));
        loadMoreButton.setAlignmentX(CENTER_ALIGNMENT);
        loadMoreButton.addActionListener(e -> startSearch(false));
        loadMoreButton.setVisible(false);
        localizedButtons.put("load-more", loadMoreButton);

        tabs.addTab(ModrinthStrings.get("tab.browse"), scrollable(resultsBox));
        tabs.addTab(ModrinthStrings.get("tab.installed"), scrollable(installedBox));
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) {
                refreshInstalled();
            }
        });
        return tabs;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(SwingUtil.magnify(8), 0));
        bar.setOpaque(false);
        bar.add(statusLabel, BorderLayout.CENTER);

        bar.add(button("refresh", "refresh", e -> {
            if (tabs.getSelectedIndex() == 1) {
                refreshInstalled();
            } else {
                startSearch(true);
            }
        }), BorderLayout.EAST);
        return bar;
    }

    private static JScrollPane scrollable(JComponent content) {
        JPanel wrapper = new WidthTrackingPanel();
        wrapper.add(content, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(wrapper,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(SwingUtil.magnify(16));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        return scroll;
    }

    /**
     * Content holder for the scroll panes.
     * <p>
     * Horizontal scrolling is switched off, so without this a row that wants to be wider
     * than the viewport is simply cut off on the right - which is where the install
     * buttons live. Tracking the viewport width instead forces every row to fit, and the
     * row's own layout gives the buttons their space before the description gets the rest.
     */
    private static class WidthTrackingPanel extends JPanel implements Scrollable {
        WidthTrackingPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return SwingUtil.magnify(16);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // ------------------------------------------------------------ target

    /**
     * Re-reads the target this panel installs into. Called every time the screen is
     * shown, because the user may have switched versions or instances in between.
     */
    public void onShown() {
        ModTarget newTarget = targetSource.get();

        boolean sameTarget = newTarget != null && target != null
                && StringUtils.equals(newTarget.getVersionId(), target.getVersionId())
                && newTarget.getGameDir().equals(target.getGameDir());

        target = newTarget;
        installer = target == null ? null : new ModInstaller(target, type);

        updateTargetLabel();
        loadGameVersionsOnce();
        syncFiltersFromTarget();

        if (!sameTarget || resultsBox.getComponentCount() == 0) {
            startSearch(true);
        }
        if (tabs.getSelectedIndex() == 1) {
            refreshInstalled();
        }
    }

    private void updateTargetLabel() {
        if (!standalone) {
            return;
        }
        if (target == null) {
            targetLabel.setText(ModrinthStrings.get("no-version-selected"));
            return;
        }
        targetLabel.setText(ModrinthStrings.get("target", target.getVersionId()));
    }

    /**
     * Pushes the detected game version and loader into the combo boxes without letting
     * their listeners kick off a second search.
     */
    private boolean syncingFilters;

    /**
     * Set while the selected Minecraft version cannot load mods on its own, so the notice
     * survives a search finishing and overwriting the status line.
     */
    private String targetWarning;

    private void syncFiltersFromTarget() {
        syncingFilters = true;
        try {
            if (target != null && StringUtils.isNotEmpty(target.getGameVersion())) {
                gameVersionBox.setSelectedItem(target.getGameVersion());
            }
            if (target != null && target.getLoader() != null) {
                loaderBox.setSelectedItem(target.getLoader());
            }
        } finally {
            syncingFilters = false;
        }

        if (type.isLoaderSpecific() && target != null && !target.supportsMods()) {
            targetWarning = ModrinthStrings.get("vanilla");
            // browsing and installing both need a loader to ask Modrinth about, so fall
            // back to whatever the loader box happens to show
            Object fallback = loaderBox.getSelectedItem();
            if (fallback instanceof ModLoader) {
                target = target.withLoader((ModLoader) fallback);
                installer = new ModInstaller(target, type);
            }
        } else {
            targetWarning = null;
        }
        setStatus(targetWarning == null ? "" : targetWarning);
    }

    private void onGameVersionChanged() {
        if (syncingFilters || target == null) {
            return;
        }
        Object selected = gameVersionBox.getSelectedItem();
        String version = selected == null ? null : selected.toString().trim();
        if (StringUtils.equals(version, target.getGameVersion())) {
            return;
        }
        target = target.withGameVersion(StringUtils.isEmpty(version) ? null : version);
        installer = new ModInstaller(target, type);
        startSearch(true);
    }

    private void onLoaderChanged() {
        if (syncingFilters || target == null) {
            return;
        }
        Object selected = loaderBox.getSelectedItem();
        ModLoader loader = selected instanceof ModLoader ? (ModLoader) selected : null;
        if (loader == target.getLoader()) {
            return;
        }
        target = target.withLoader(loader);
        installer = new ModInstaller(target, type);
        startSearch(true);
    }

    private boolean gameVersionsLoaded;

    private void loadGameVersionsOnce() {
        if (gameVersionsLoaded) {
            return;
        }
        gameVersionsLoaded = true;
        AsyncThread.execute(() -> {
            final List<String> versions;
            try {
                versions = ModrinthApi.listReleaseGameVersions();
            } catch (IOException e) {
                log.warn("Could not load the Modrinth game version list: {}", e.toString());
                return;
            }
            SwingUtil.later(() -> {
                Object selected = gameVersionBox.getSelectedItem();
                syncingFilters = true;
                try {
                    gameVersionBox.setModel(new DefaultComboBoxModel<>(versions.toArray(new String[0])));
                    if (selected != null) {
                        gameVersionBox.setSelectedItem(selected);
                    }
                } finally {
                    syncingFilters = false;
                }
            });
        });
    }

    // ------------------------------------------------------------ searching

    private void startSearch(boolean reset) {
        final String query = searchField.getText().trim();
        final String gameVersion = target == null ? null : target.getGameVersion();
        final String loader = target == null || target.getLoader() == null
                ? null : target.getLoader().getId();
        final SortOption sort = (SortOption) sortBox.getSelectedItem();
        final int offset = reset ? 0 : nextOffset;

        // Several widgets can ask for a search in response to one user action - selecting
        // a game version also repopulates its own combo box, for instance. Firing the same
        // request twice only burns Modrinth's rate limit, so an identical query that is
        // already in flight is dropped.
        String key = query + ' ' + gameVersion + ' ' + loader
                + ' ' + (sort == null ? "" : sort.getIndex()) + ' ' + offset;
        if (key.equals(inFlightSearch)) {
            log.debug("Search already in flight, ignoring duplicate request");
            return;
        }
        inFlightSearch = key;

        if (reset) {
            nextOffset = 0;
            resultsBox.removeAll();
            resultsBox.revalidate();
            resultsBox.repaint();
        }
        loadMoreButton.setVisible(false);

        final int generation = ++searchGeneration;

        setStatus(ModrinthStrings.get("loading"));

        AsyncThread.execute(() -> {
            final ModrinthSearchResult result;
            try {
                result = ModrinthApi.search(type, query, gameVersion, loader,
                        sort == null ? SortOption.RELEVANCE.getIndex() : sort.getIndex(),
                        offset, PAGE_SIZE);
            } catch (IOException e) {
                log.warn("Modrinth search failed", e);
                SwingUtil.later(() -> {
                    inFlightSearch = null;
                    if (generation != searchGeneration) {
                        return;
                    }
                    setStatus(ModrinthStrings.get("error.search") + " " + e.getMessage());
                });
                return;
            }
            SwingUtil.later(() -> {
                inFlightSearch = null;
                if (generation != searchGeneration) {
                    return; // a newer search already owns the list
                }
                showResults(result);
            });
        });
    }

    private void showResults(ModrinthSearchResult result) {
        resultsBox.remove(loadMoreButton);

        for (ModrinthProject project : result.getHits()) {
            resultsBox.add(new ModrinthProjectCell(this, project));
        }
        nextOffset = result.getOffset() + result.getHits().size();

        if (result.hasMore()) {
            resultsBox.add(loadMoreButton);
            loadMoreButton.setVisible(true);
        }

        if (resultsBox.getComponentCount() == 0) {
            setStatus(ModrinthStrings.get("empty"));
        } else {
            setStatus(targetWarning == null ? "" : targetWarning);
        }

        resultsBox.revalidate();
        resultsBox.repaint();
    }

    // ------------------------------------------------------------ installing

    /**
     * Finds a build of the project that fits the current target and installs it.
     * Runs off the Swing thread; the cell is told about progress and the outcome.
     */
    void install(ModrinthProject project, ModrinthProjectCell cell) {
        final ModTarget currentTarget = target;
        final ModInstaller currentInstaller = installer;
        if (currentTarget == null || currentInstaller == null) {
            Alert.showError(ModrinthStrings.get("error.title"),
                    ModrinthStrings.get("no-version-selected"));
            return;
        }
        final boolean withDependencies = type.isLoaderSpecific() && dependenciesBox.isSelected();

        cell.setBusy(ModrinthStrings.get("installing"));

        AsyncThread.execute(() -> {
            try {
                List<ModrinthVersion> versions = ModrinthApi.listVersions(
                        type,
                        project.getProjectId(),
                        currentTarget.getGameVersion(),
                        currentTarget.getLoader() == null ? null : currentTarget.getLoader().getId()
                );
                ModrinthVersion best = ModInstaller.pickBest(versions);
                if (best == null) {
                    String loaderName = currentTarget.getLoader() == null
                            ? "-" : currentTarget.getLoader().getDisplayName();
                    final String message = ModrinthStrings.get("no-compatible-version",
                            StringUtils.defaultString(currentTarget.getGameVersion(), "?"), loaderName);
                    SwingUtil.later(() -> {
                        cell.setIdle();
                        setStatus(message);
                    });
                    return;
                }

                final List<String> installed = currentInstaller.install(best, withDependencies,
                        (fileName, current, total) -> SwingUtil.later(() ->
                                cell.setBusy(ModrinthStrings.get("installing") + " " + current + "/" + total)));

                SwingUtil.later(() -> {
                    cell.setInstalled();
                    setStatus(ModrinthStrings.get("installed-into",
                            installed.size(), currentInstaller.getDirectory()));
                    if (tabs.getSelectedIndex() == 1) {
                        refreshInstalled();
                    }
                });
            } catch (IOException e) {
                log.warn("Could not install {}", project, e);
                SwingUtil.later(() -> {
                    cell.setIdle();
                    Alert.showError(ModrinthStrings.get("error.title"),
                            ModrinthStrings.get("error.install") + "\n" + e.getMessage());
                });
            }
        });
    }

    // ------------------------------------------------------------ installed tab

    private void refreshInstalled() {
        installedBox.removeAll();

        if (installer == null) {
            installedBox.add(new JLabel(ModrinthStrings.get("no-version-selected")));
        } else {
            List<InstalledMod> mods = installer.listInstalled();
            if (mods.isEmpty()) {
                installedBox.add(new JLabel(ModrinthStrings.get("empty.installed")));
            } else {
                for (InstalledMod mod : mods) {
                    installedBox.add(new InstalledModCell(this, mod));
                }
            }
        }

        installedBox.revalidate();
        installedBox.repaint();
    }

    void deleteInstalled(InstalledMod mod) {
        if (installer == null) {
            return;
        }
        if (!Alert.showQuestion(ModrinthStrings.get("error.title"),
                ModrinthStrings.get("confirm.delete", mod.getDisplayName()))) {
            return;
        }
        try {
            installer.delete(mod);
        } catch (IOException e) {
            log.warn("Could not delete {}", mod, e);
            Alert.showError(ModrinthStrings.get("error.title"),
                    ModrinthStrings.get("error.delete") + "\n" + e.getMessage());
        }
        refreshInstalled();
    }

    void toggleInstalled(InstalledMod mod) {
        if (installer == null) {
            return;
        }
        try {
            installer.setEnabled(mod, !mod.isEnabled());
        } catch (IOException e) {
            log.warn("Could not toggle {}", mod, e);
            Alert.showError(ModrinthStrings.get("error.title"), String.valueOf(e.getMessage()));
        }
        refreshInstalled();
    }

    private void openContentFolder() {
        if (installer == null) {
            Alert.showError(ModrinthStrings.get("error.title"),
                    ModrinthStrings.get("no-version-selected"));
            return;
        }
        final File dir = installer.getDirectory();
        AsyncThread.execute(() -> {
            dir.mkdirs();
            OS.openFolder(dir);
        });
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    /**
     * The orderings Modrinth's search index offers.
     */
    enum SortOption {
        RELEVANCE("relevance", "sort.relevance"),
        DOWNLOADS("downloads", "sort.downloads"),
        FOLLOWS("follows", "sort.follows"),
        NEWEST("newest", "sort.newest"),
        UPDATED("updated", "sort.updated");

        private final String index;
        private final String key;

        SortOption(String index, String key) {
            this.index = index;
            this.key = key;
        }

        String getIndex() {
            return index;
        }

        @Override
        public String toString() {
            return ModrinthStrings.get(key);
        }
    }

    /**
     * Redoes every caption after the user switches the launcher's language. The result
     * cells are rebuilt from scratch instead, because they are cheap and short-lived.
     */
    @Override
    public void updateLocale() {
        for (Map.Entry<String, JButton> entry : localizedButtons.entrySet()) {
            entry.getValue().setText(ModrinthStrings.get(entry.getKey()));
        }
        for (Map.Entry<String, JLabel> entry : localizedLabels.entrySet()) {
            entry.getValue().setText(ModrinthStrings.get(entry.getKey()) + ":");
        }
        dependenciesBox.setText(ModrinthStrings.get("dependencies"));
        searchField.putClientProperty("JTextField.placeholderText", ModrinthStrings.get("search.hint"));
        tabs.setTitleAt(0, ModrinthStrings.get("tab.browse"));
        tabs.setTitleAt(1, ModrinthStrings.get("tab.installed"));
        sortBox.repaint();
        updateTargetLabel();
        refreshInstalled();
    }

    @Override
    public Dimension getPreferredSize() {
        return SwingUtil.magnify(new Dimension(760, 560));
    }

    /**
     * Kept so the standalone screen can still be built from a {@link VersionSyncInfo}
     * without every caller repeating the settings lookup.
     */
    public static Supplier<ModTarget> targetOfSelectedVersion(MainPane pane) {
        return () -> ModTarget.of(
                pane.defaultScene.loginForm.versions.getVersion(),
                LegacyLauncher.getInstance().getSettings()
        );
    }
}
