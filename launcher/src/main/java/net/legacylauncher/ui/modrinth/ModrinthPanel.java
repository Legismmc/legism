package net.legacylauncher.ui.modrinth;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
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
import net.legacylauncher.ui.swing.extended.BorderPanel;
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
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Modrinth browser: search the mod index, install into the mods directory of the
 * version selected in the launcher, and manage what is already installed.
 */
@Slf4j
public class ModrinthPanel extends BorderPanel implements LocalizableComponent {
    private static final int PAGE_SIZE = 20;

    private final MainPane pane;

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

    public ModrinthPanel(MainPane pane) {
        this.pane = pane;
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

        JButton back = button("back", "arrow-left", e -> pane.openDefaultScene());
        top.add(back, BorderLayout.WEST);

        targetLabel.setHorizontalAlignment(SwingConstants.CENTER);
        targetLabel.setFont(targetLabel.getFont().deriveFont(Font.BOLD));
        top.add(targetLabel, BorderLayout.CENTER);

        top.add(button("open-folder", "folder-open", e -> openModsFolder()), BorderLayout.EAST);

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
        gameVersionBox.setEditable(true);
        gameVersionBox.setPrototypeDisplayValue("1.00.00");
        gameVersionBox.addActionListener(e -> onGameVersionChanged());
        filters.add(gameVersionBox);

        filters.add(label("loader"));
        loaderBox.setModel(new DefaultComboBoxModel<>(new Object[]{
                ModLoader.FABRIC, ModLoader.FORGE, ModLoader.NEOFORGE, ModLoader.QUILT
        }));
        loaderBox.addActionListener(e -> onLoaderChanged());
        filters.add(loaderBox);

        filters.add(label("sort"));
        sortBox.setModel(new DefaultComboBoxModel<>(SortOption.values()));
        sortBox.addActionListener(e -> startSearch(true));
        filters.add(sortBox);

        dependenciesBox.setText(ModrinthStrings.get("dependencies"));
        dependenciesBox.setSelected(true);
        dependenciesBox.setOpaque(false);
        filters.add(dependenciesBox);

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
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
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

    // ------------------------------------------------------------ target

    /**
     * Re-reads the version selected on the main screen. Called every time the screen is
     * opened, because the user may have switched versions in between.
     */
    public void onShown() {
        VersionSyncInfo selected = pane.defaultScene.loginForm.versions.getVersion();
        ModTarget newTarget = ModTarget.of(selected, LegacyLauncher.getInstance().getSettings());

        boolean sameTarget = newTarget != null && target != null
                && StringUtils.equals(newTarget.getVersionId(), target.getVersionId());

        target = newTarget;
        installer = target == null ? null : new ModInstaller(target);

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

        if (target != null && !target.supportsMods()) {
            targetWarning = ModrinthStrings.get("vanilla");
            // browsing and installing both need a loader to ask Modrinth about, so fall
            // back to whatever the loader box happens to show
            Object fallback = loaderBox.getSelectedItem();
            if (fallback instanceof ModLoader) {
                target = target.withLoader((ModLoader) fallback);
                installer = new ModInstaller(target);
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
        installer = new ModInstaller(target);
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
        installer = new ModInstaller(target);
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
        if (reset) {
            nextOffset = 0;
            resultsBox.removeAll();
            resultsBox.revalidate();
            resultsBox.repaint();
        }
        loadMoreButton.setVisible(false);

        final int generation = ++searchGeneration;
        final String query = searchField.getText().trim();
        final int offset = nextOffset;
        final String gameVersion = target == null ? null : target.getGameVersion();
        final String loader = target == null || target.getLoader() == null
                ? null : target.getLoader().getId();
        final SortOption sort = (SortOption) sortBox.getSelectedItem();

        setStatus(ModrinthStrings.get("loading"));

        AsyncThread.execute(() -> {
            final ModrinthSearchResult result;
            try {
                result = ModrinthApi.search(query, gameVersion, loader,
                        sort == null ? SortOption.RELEVANCE.getIndex() : sort.getIndex(),
                        offset, PAGE_SIZE);
            } catch (IOException e) {
                log.warn("Modrinth search failed", e);
                SwingUtil.later(() -> {
                    if (generation != searchGeneration) {
                        return;
                    }
                    setStatus(ModrinthStrings.get("error.search") + " " + e.getMessage());
                });
                return;
            }
            SwingUtil.later(() -> {
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
        final boolean withDependencies = dependenciesBox.isSelected();

        cell.setBusy(ModrinthStrings.get("installing"));

        AsyncThread.execute(() -> {
            try {
                List<ModrinthVersion> versions = ModrinthApi.listVersions(
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
                            installed.size(), currentTarget.getModsDir()));
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
            Alert.showError(ModrinthStrings.get("error.title"), e.getMessage());
        }
        refreshInstalled();
    }

    private void openModsFolder() {
        if (target == null) {
            Alert.showError(ModrinthStrings.get("error.title"),
                    ModrinthStrings.get("no-version-selected"));
            return;
        }
        final File dir = target.getModsDir();
        AsyncThread.execute(() -> {
            //noinspection ResultOfMethodCallIgnored
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
}
