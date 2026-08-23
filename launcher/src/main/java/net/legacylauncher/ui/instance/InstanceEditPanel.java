package net.legacylauncher.ui.instance;

import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.modrinth.ContentType;
import net.legacylauncher.modrinth.ModTarget;
import net.legacylauncher.ui.MainPane;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.loc.LocalizableComponent;
import net.legacylauncher.ui.modrinth.ModrinthPanel;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.modrinth.WorldsPanel;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;
import net.minecraft.launcher.updater.VersionSyncInfo;
import net.minecraft.launcher.versions.CompleteVersion;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything you can change about one instance: its mods, resource packs, shaders and
 * worlds, each in its own tab.
 * <p>
 * Every tab installs into the instance's own game directory, so two instances never see
 * each other's content.
 */
public class InstanceEditPanel extends BackdropPanel implements LocalizableComponent {

    private final MainPane pane;
    private final JLabel title = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();

    private final List<ModrinthPanel> browsers = new ArrayList<>();
    private final WorldsPanel worlds;
    private final JButton backButton;
    private final JButton playButton;
    private final JButton folderButton;

    private Instance instance;

    public InstanceEditPanel(MainPane pane) {
        this.pane = pane;
        setVgap(SwingUtil.magnify(8));

        JPanel header = new JPanel(new BorderLayout(SwingUtil.magnify(8), 0));
        header.setOpaque(false);

        backButton = new JButton(ModrinthStrings.get("back"));
        backButton.setIcon(Images.getIcon16("arrow-left"));
        backButton.addActionListener(e -> pane.openInstancesScene());
        header.add(backButton, BorderLayout.WEST);

        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        header.add(title, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(4), 0));
        right.setOpaque(false);

        folderButton = new JButton(ModrinthStrings.get("instances.open-folder"));
        folderButton.setIcon(Images.getIcon16("folder-open"));
        folderButton.addActionListener(e -> openInstanceFolder());
        right.add(folderButton);

        playButton = new JButton(ModrinthStrings.get("instances.play"));
        playButton.setIcon(Images.getIcon16("play"));
        playButton.addActionListener(e -> play());
        right.add(playButton);

        header.add(right, BorderLayout.EAST);
        setNorth(header);

        // one browser per Modrinth-backed content type, then the local worlds tab
        addBrowser(ContentType.MOD);
        addBrowser(ContentType.RESOURCE_PACK);
        addBrowser(ContentType.SHADER);

        worlds = new WorldsPanel(this::currentTarget);
        tabs.addTab(ModrinthStrings.get("tab.worlds"), worlds);

        tabs.addChangeListener(e -> showSelectedTab());
        setCenter(tabs);
    }

    private void addBrowser(ContentType type) {
        ModrinthPanel panel = new ModrinthPanel(pane, type, this::currentTarget, false);
        browsers.add(panel);
        tabs.addTab(ModrinthStrings.get(type.getTitleKey()), panel);
    }

    /**
     * Points the tabs at the instance's own game directory. Falls back to the version id
     * when the version is not installed yet, so a fresh instance can be filled with mods
     * before it has ever been started.
     */
    private ModTarget currentTarget() {
        if (instance == null) {
            return null;
        }
        File gameDir = instance.getGameDir();
        VersionSyncInfo syncInfo = LegacyLauncher.getInstance().getVersionManager()
                .getVersionSyncInfo(instance.getVersionId());
        CompleteVersion complete = syncInfo == null ? null : syncInfo.getLocalCompleteVersion();
        if (complete != null) {
            return ModTarget.of(complete, gameDir);
        }
        return ModTarget.ofVersionId(instance.getVersionId(), gameDir);
    }

    public void setInstance(Instance instance) {
        this.instance = instance;
        title.setText(ModrinthStrings.get("edit.title",
                instance == null ? "" : instance.getName() + " — " + instance.getVersionId()));
    }

    public Instance getInstance() {
        return instance;
    }

    public void onShown() {
        showSelectedTab();
    }

    /**
     * Only the visible tab is refreshed: each browser fires a Modrinth search when shown,
     * and doing that for all of them at once would be three needless requests.
     */
    private void showSelectedTab() {
        java.awt.Component selected = tabs.getSelectedComponent();
        if (selected instanceof ModrinthPanel) {
            ((ModrinthPanel) selected).onShown();
        } else if (selected instanceof WorldsPanel) {
            ((WorldsPanel) selected).onShown();
        }
    }

    private void play() {
        if (instance != null) {
            pane.openDefaultScene();
            pane.defaultScene.loginForm.startInstance(instance);
        }
    }

    private void openInstanceFolder() {
        if (instance == null) {
            return;
        }
        final File dir = instance.getGameDir();
        AsyncThread.execute(() -> {
            dir.mkdirs();
            OS.openFolder(dir);
        });
    }

    @Override
    public void updateLocale() {
        backButton.setText(ModrinthStrings.get("back"));
        playButton.setText(ModrinthStrings.get("instances.play"));
        folderButton.setText(ModrinthStrings.get("instances.open-folder"));
        tabs.setTitleAt(0, ModrinthStrings.get(ContentType.MOD.getTitleKey()));
        tabs.setTitleAt(1, ModrinthStrings.get(ContentType.RESOURCE_PACK.getTitleKey()));
        tabs.setTitleAt(2, ModrinthStrings.get(ContentType.SHADER.getTitleKey()));
        tabs.setTitleAt(3, ModrinthStrings.get("tab.worlds"));
        setInstance(instance);
    }

    @Override
    public Dimension getPreferredSize() {
        return SwingUtil.magnify(new Dimension(820, 580));
    }
}
