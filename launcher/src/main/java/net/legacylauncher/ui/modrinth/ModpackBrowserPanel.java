package net.legacylauncher.ui.modrinth;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.modrinth.ContentFile;
import net.legacylauncher.modrinth.ContentProject;
import net.legacylauncher.modrinth.ContentProvider;
import net.legacylauncher.modrinth.ContentProviders;
import net.legacylauncher.modrinth.ContentSearchResult;
import net.legacylauncher.modrinth.ContentType;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Browses whole modpacks and installs the chosen one as a new instance.
 * <p>
 * Deliberately not a tab of {@link ModrinthPanel}: every other kind of content is
 * installed *into* an existing instance, while a modpack becomes one, so this screen is
 * reached from the instance list rather than from an instance's own editor.
 */
@Slf4j
public class ModpackBrowserPanel extends JPanel implements ContentCellHost {
    private static final int PAGE_SIZE = 20;

    /**
     * Told whenever a pack finished installing, so the instance grid behind this screen
     * picks the new instance up.
     */
    private final Runnable onInstalled;

    private final JTextField searchField = new JTextField();
    private final JComboBox<ContentProvider> libraryBox = new JComboBox<>();
    private final JComboBox<String> gameVersionBox = new JComboBox<>();
    private final JPanel resultsBox = new JPanel();
    private final JLabel statusLabel = new JLabel();
    private final JButton loadMoreButton = new JButton(ModrinthStrings.get("load-more"));

    private ContentProvider provider;
    private int searchGeneration;
    private int nextOffset;
    private boolean gameVersionsLoaded;

    public ModpackBrowserPanel(Runnable onInstalled) {
        super(new BorderLayout(0, SwingUtil.magnify(8)));
        this.onInstalled = onInstalled;
        setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(8), SwingUtil.magnify(8), SwingUtil.magnify(8), SwingUtil.magnify(8)));

        List<ContentProvider> providers = new ArrayList<>();
        for (ContentProvider candidate : ContentProviders.all()) {
            if (candidate.supports(ContentType.MODPACK)) {
                providers.add(candidate);
            }
        }
        provider = providers.isEmpty() ? ContentProviders.getDefault() : providers.get(0);

        add(buildHeader(providers), BorderLayout.NORTH);

        resultsBox.setLayout(new BoxLayout(resultsBox, BoxLayout.Y_AXIS));
        resultsBox.setOpaque(false);
        JScrollPane scroll = new JScrollPane(resultsBox,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(SwingUtil.magnify(16));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        add(statusLabel, BorderLayout.SOUTH);

        loadMoreButton.setAlignmentX(CENTER_ALIGNMENT);
        loadMoreButton.addActionListener(e -> startSearch(false));
        loadMoreButton.setVisible(false);

        loadGameVersionsOnce();
        startSearch(true);
    }

    private JComponent buildHeader(List<ContentProvider> providers) {
        JPanel header = new JPanel(new BorderLayout(SwingUtil.magnify(8), SwingUtil.magnify(6)));
        header.setOpaque(false);

        searchField.putClientProperty("JTextField.placeholderText", ModrinthStrings.get("modpack.search.hint"));
        searchField.addActionListener(e -> startSearch(true));
        header.add(searchField, BorderLayout.CENTER);

        JButton search = new JButton(ModrinthStrings.get("search"));
        search.setIcon(Images.getIcon16("search"));
        search.addActionListener(e -> startSearch(true));
        header.add(search, BorderLayout.EAST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(6), 0));
        filters.setOpaque(false);
        filters.add(new JLabel(ModrinthStrings.get("library") + ":"));
        libraryBox.setModel(new DefaultComboBoxModel<>(providers.toArray(new ContentProvider[0])));
        // without this the box shows the provider's raw toString()
        libraryBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof ContentProvider) {
                    ContentProvider candidate = (ContentProvider) value;
                    setText(candidate.getDisplayName());
                    setEnabled(candidate.isAvailable());
                }
                return this;
            }
        });
        libraryBox.setSelectedItem(provider);
        libraryBox.addActionListener(e -> {
            Object selected = libraryBox.getSelectedItem();
            if (selected instanceof ContentProvider && selected != provider) {
                provider = (ContentProvider) selected;
                gameVersionsLoaded = false;
                loadGameVersionsOnce();
                startSearch(true);
            }
        });
        filters.add(libraryBox);

        filters.add(new JLabel(ModrinthStrings.get("game-version") + ":"));
        gameVersionBox.setModel(new DefaultComboBoxModel<>(
                new String[]{ModrinthStrings.get("modpack.any-version")}));
        gameVersionBox.addActionListener(e -> startSearch(true));
        filters.add(gameVersionBox);

        header.add(filters, BorderLayout.SOUTH);
        return header;
    }

    private String selectedGameVersion() {
        Object selected = gameVersionBox.getSelectedItem();
        if (selected == null) {
            return null;
        }
        String value = selected.toString();
        // the first entry is the "any version" placeholder, not a real version
        return value.equals(ModrinthStrings.get("modpack.any-version")) ? null : value;
    }

    private void loadGameVersionsOnce() {
        if (gameVersionsLoaded) {
            return;
        }
        gameVersionsLoaded = true;
        final ContentProvider currentProvider = provider;
        AsyncThread.execute(() -> {
            final List<String> versions;
            try {
                versions = currentProvider.listGameVersions();
            } catch (IOException e) {
                log.warn("Could not load the game version list: {}", e.toString());
                return;
            }
            SwingUtil.later(() -> {
                List<String> items = new ArrayList<>();
                items.add(ModrinthStrings.get("modpack.any-version"));
                items.addAll(versions);
                gameVersionBox.setModel(new DefaultComboBoxModel<>(items.toArray(new String[0])));
            });
        });
    }

    private void startSearch(boolean reset) {
        final String query = searchField.getText().trim();
        final String gameVersion = selectedGameVersion();
        final ContentProvider currentProvider = provider;
        final int offset = reset ? 0 : nextOffset;

        if (reset) {
            nextOffset = 0;
            resultsBox.removeAll();
            resultsBox.revalidate();
            resultsBox.repaint();
        }
        loadMoreButton.setVisible(false);

        if (!currentProvider.isAvailable()) {
            setStatus(currentProvider.getUnavailableReason());
            return;
        }

        final int generation = ++searchGeneration;
        setStatus(ModrinthStrings.get("loading"));

        AsyncThread.execute(() -> {
            final ContentSearchResult result;
            try {
                result = currentProvider.search(ContentType.MODPACK, query, gameVersion,
                        null, null, offset, PAGE_SIZE);
            } catch (IOException e) {
                log.warn("Modpack search failed", e);
                SwingUtil.later(() -> {
                    if (generation == searchGeneration) {
                        setStatus(ModrinthStrings.get("error.search") + " " + e.getMessage());
                    }
                });
                return;
            }
            SwingUtil.later(() -> {
                if (generation == searchGeneration) {
                    showResults(result);
                }
            });
        });
    }

    private void showResults(ContentSearchResult result) {
        resultsBox.remove(loadMoreButton);
        for (ContentProject project : result.getHits()) {
            resultsBox.add(new ModrinthProjectCell(this, project));
        }
        nextOffset = result.getOffset() + result.getHits().size();
        if (result.hasMore()) {
            resultsBox.add(loadMoreButton);
            loadMoreButton.setVisible(true);
        }
        setStatus(resultsBox.getComponentCount() == 0 ? ModrinthStrings.get("empty") : "");
        resultsBox.revalidate();
        resultsBox.repaint();
    }

    @Override
    public void install(ContentProject project, ModrinthProjectCell cell) {
        final ContentProvider currentProvider = provider;
        final String gameVersion = selectedGameVersion();
        cell.setBusy(ModrinthStrings.get("installing"));

        AsyncThread.execute(() -> {
            File pack = null;
            try {
                List<ContentFile> plan = currentProvider.plan(ContentType.MODPACK, project.getId(),
                        gameVersion, null, false);
                if (plan.isEmpty()) {
                    SwingUtil.later(() -> {
                        cell.setIdle();
                        setStatus(ModrinthStrings.get("modpack.no-version"));
                    });
                    return;
                }

                SwingUtil.later(() -> cell.setBusy(ModrinthStrings.get("modpack.downloading")));
                pack = ModpackImporter.downloadToTemp(plan.get(0));

                final Instance created = ModpackImporter.importAny(pack,
                        LegacyLauncher.getInstance().getInstanceManager(),
                        (message, current, total) -> SwingUtil.later(() ->
                                cell.setBusy(ModrinthStrings.get("installing") + " " + current + "/" + total)));

                SwingUtil.later(() -> {
                    cell.setInstalled();
                    setStatus(ModrinthStrings.get("modpack.installed", created.getName()));
                    if (onInstalled != null) {
                        onInstalled.run();
                    }
                });
            } catch (IOException e) {
                log.warn("Could not install the modpack {}", project, e);
                SwingUtil.later(() -> {
                    cell.setIdle();
                    Alert.showError(ModrinthStrings.get("error.title"),
                            ModrinthStrings.get("modpack.error.install") + "\n" + e.getMessage());
                });
            } finally {
                if (pack != null && !pack.delete()) {
                    pack.deleteOnExit();
                }
            }
        });
    }

    /**
     * Always false: an installed modpack becomes an instance, and an instance keeps no
     * record of the library project it came from, so there is nothing to match against.
     */
    @Override
    public boolean isProjectInstalled(String projectId) {
        return false;
    }

    private void setStatus(String text) {
        statusLabel.setText(text == null ? "" : text);
    }
}
