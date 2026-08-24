package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.configuration.BuildConfig;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.instance.InstanceManager;
import net.legacylauncher.managers.ProfileManager;
import net.legacylauncher.managers.ProfileManagerListener;
import net.legacylauncher.minecraft.auth.Account;
import net.legacylauncher.minecraft.auth.AuthenticatorDatabase;
import net.legacylauncher.ui.MainPane;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.loc.LocalizableComponent;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.scenes.DefaultScene;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.MinecraftUtil;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;
import net.legacylauncher.user.User;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The instance manager screen: a toolbar across the top, the instances as a grid of tiles
 * grouped by their group, a column of actions for the selected one on the right, and a
 * status line along the bottom.
 */
@Slf4j
public class InstancesPanel extends BackdropPanel implements LocalizableComponent {

    private final MainPane pane;

    private final JPanel grid = new JPanel();
    private final InstanceActionsPanel sidebar;
    private JButton accountButton;
    private final JLabel statusLeft = new JLabel();
    private final JLabel statusRight = new JLabel();

    private final Map<String, JButton> toolbarButtons = new LinkedHashMap<>();
    private final List<InstanceTile> tiles = new ArrayList<>();

    /**
     * Groups the user has folded away. Kept in memory only: it is a view preference, not
     * something worth writing to disk.
     */
    private final List<String> collapsed = new ArrayList<>();

    private Instance selected;

    public InstancesPanel(MainPane pane) {
        this.pane = pane;
        this.sidebar = new InstanceActionsPanel(this);
        setVgap(SwingUtil.magnify(6));

        setNorth(buildToolbar());
        setCenter(buildBody());
        setSouth(buildStatusBar());

        // the game starting or stopping swaps Play for Stop in the sidebar; the callback
        // arrives off the Swing thread when Minecraft exits
        LegacyLauncher.getInstance().getInstanceManager().addListener(instances ->
                SwingUtil.later(() -> {
                    applySelection();
                    updateStatus();
                }));

        // keep the toolbar showing whoever is signed in
        LegacyLauncher.getInstance().getProfileManager().addListener(new ProfileManagerListener() {
            @Override
            public void onProfilesRefreshed(ProfileManager pm) {
                SwingUtil.later(InstancesPanel.this::updateAccountButton);
            }

            @Override
            public void onProfileManagerChanged(ProfileManager pm) {
                SwingUtil.later(InstancesPanel.this::updateAccountButton);
            }

            @Override
            public void onAccountsRefreshed(AuthenticatorDatabase db) {
                SwingUtil.later(InstancesPanel.this::updateAccountButton);
            }
        });
    }

    // ---------------------------------------------------------------- layout

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(4), SwingUtil.magnify(2)));
        left.setOpaque(false);
        left.add(toolbarButton("instances.create", "plus", e -> createInstance()));
        left.add(toolbarButton("instances.folders", "folder-open", this::showFoldersMenu));
        left.add(toolbarButton("instances.settings", "gear", e -> openLauncherSettings()));
        left.add(toolbarButton("instances.help", "question", this::showHelpMenu));
        left.add(toolbarButton("refresh", "refresh", e -> refresh()));
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(4), SwingUtil.magnify(2)));
        right.setOpaque(false);
        accountButton = new JButton();
        accountButton.setIcon(Images.getIcon16("user-circle-o"));
        accountButton.addActionListener(e -> pane.openAccountEditor());
        right.add(accountButton);
        bar.add(right, BorderLayout.EAST);

        updateAccountButton();

        return bar;
    }

    /**
     * Shows who is signed in, falling back to the generic caption when nobody is. The
     * account type comes along because the same nickname can exist twice under different
     * kinds of account.
     */
    private void updateAccountButton() {
        Account<? extends User> account = pane.defaultScene.loginForm.accounts.getAccount();
        if (account == null) {
            accountButton.setText(ModrinthStrings.get("instances.accounts"));
            accountButton.setToolTipText(ModrinthStrings.get("instances.accounts"));
            return;
        }
        accountButton.setText(account.getDisplayName());
        accountButton.setToolTipText(account.getDisplayName() + " ["
                + account.getType().toString().toLowerCase(Locale.ROOT) + "]");
    }

    private JButton toolbarButton(String key, String icon, ActionListener action) {
        JButton button = new JButton(ModrinthStrings.get(key));
        button.setIcon(Images.getIcon16(icon));
        button.addActionListener(action);
        toolbarButtons.put(key, button);
        return button;
    }

    private JComponent buildBody() {
        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
        grid.setOpaque(false);

        JPanel wrapper = new WidthTrackingPanel();
        wrapper.add(grid, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(wrapper,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(SwingUtil.magnify(16));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);

        // clicking the empty space clears the selection, like any file manager
        wrapper.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    emptyAreaMenu().show(e.getComponent(), e.getX(), e.getY());
                } else {
                    select(null);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    emptyAreaMenu().show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        JPanel body = new JPanel(new BorderLayout(SwingUtil.magnify(8), 0));
        body.setOpaque(false);
        body.add(scroll, BorderLayout.CENTER);

        JPanel rightSide = new JPanel(new BorderLayout());
        rightSide.setOpaque(false);
        rightSide.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.WEST);
        rightSide.add(sidebar, BorderLayout.CENTER);
        body.add(rightSide, BorderLayout.EAST);

        return body;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(SwingUtil.magnify(8), 0));
        bar.setOpaque(false);
        statusLeft.setEnabled(false);
        statusRight.setEnabled(false);
        statusRight.setHorizontalAlignment(SwingConstants.RIGHT);
        bar.add(statusLeft, BorderLayout.WEST);
        bar.add(statusRight, BorderLayout.EAST);
        return bar;
    }

    /**
     * Keeps rows from demanding more width than the viewport has, which would cut the grid
     * off on the right where horizontal scrolling is switched off.
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

    // ------------------------------------------------------------ contents

    private InstanceManager manager() {
        return LegacyLauncher.getInstance().getInstanceManager();
    }

    public void onShown() {
        updateAccountButton();
        refresh();
    }

    public void refresh() {
        List<Instance> instances = manager().refresh();

        grid.removeAll();
        tiles.clear();

        if (instances.isEmpty()) {
            JLabel empty = new JLabel(ModrinthStrings.get("instances.empty"));
            empty.setAlignmentX(LEFT_ALIGNMENT);
            empty.setBorder(BorderFactory.createEmptyBorder(
                    SwingUtil.magnify(8), SwingUtil.magnify(8), 0, 0));
            grid.add(empty);
        } else {
            Map<String, List<Instance>> byGroup = new LinkedHashMap<>();
            byGroup.put("", new ArrayList<>());
            for (Instance instance : instances) {
                List<Instance> members = byGroup.get(instance.getGroup());
                if (members == null) {
                    members = new ArrayList<>();
                    byGroup.put(instance.getGroup(), members);
                }
                members.add(instance);
            }
            for (Map.Entry<String, List<Instance>> entry : byGroup.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                grid.add(new GroupHeader(entry.getKey()));
                if (!collapsed.contains(entry.getKey())) {
                    grid.add(buildRow(entry.getValue()));
                }
            }
        }

        // the selected instance is a stale object after a refresh; match it up again by id
        if (selected != null) {
            Instance again = null;
            for (Instance instance : instances) {
                if (instance.getId().equals(selected.getId())) {
                    again = instance;
                    break;
                }
            }
            selected = again;
        }
        applySelection();
        updateStatus();

        grid.revalidate();
        grid.repaint();
    }

    /**
     * Lays one group's tiles out left to right, wrapping when the row runs out of width.
     */
    private JComponent buildRow(List<Instance> instances) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(4), SwingUtil.magnify(4)));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);

        for (Instance instance : instances) {
            InstanceTile tile = new InstanceTile(instance);
            tile.addMouseListener(new TileMouse(tile));
            for (Component child : tile.getComponents()) {
                child.addMouseListener(new TileMouse(tile));
            }
            tiles.add(tile);
            row.add(tile);
        }
        return row;
    }

    /**
     * A group's caption; clicking it folds the group away.
     */
    private class GroupHeader extends JPanel {
        GroupHeader(final String group) {
            setLayout(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(4), SwingUtil.magnify(2)));
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);

            final boolean folded = collapsed.contains(group);
            JLabel caption = new JLabel((folded ? "▶  " : "▼  ")
                    + (group.isEmpty() ? ModrinthStrings.get("instances.default-group") : group));
            caption.setFont(caption.getFont().deriveFont(Font.BOLD));
            caption.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            caption.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (folded) {
                        collapsed.remove(group);
                    } else {
                        collapsed.add(group);
                    }
                    refresh();
                }
            });
            add(caption);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private class TileMouse extends MouseAdapter {
        private final InstanceTile tile;

        TileMouse(InstanceTile tile) {
            this.tile = tile;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            select(tile.getInstance());
            maybePopup(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            maybePopup(e);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                play(tile.getInstance());
            }
        }

        private void maybePopup(MouseEvent e) {
            if (e.isPopupTrigger()) {
                select(tile.getInstance());
                menuFor(tile.getInstance()).show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }

    private void select(Instance instance) {
        selected = instance;
        applySelection();
        updateStatus();
    }

    private void applySelection() {
        for (InstanceTile tile : tiles) {
            tile.setSelected(selected != null
                    && tile.getInstance().getId().equals(selected.getId()));
        }
        Instance running = manager().getRunning();
        sidebar.setInstance(selected,
                selected != null && running != null && running.getId().equals(selected.getId()));
    }

    private void updateStatus() {
        if (selected == null) {
            statusLeft.setText("");
        } else if (selected.getLastPlayed() <= 0L) {
            statusLeft.setText(selected.getName() + " — "
                    + ModrinthStrings.get("instances.never-played"));
        } else {
            String when = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(selected.getLastPlayed()));
            statusLeft.setText(selected.getName() + " — "
                    + ModrinthStrings.get("instances.last-played", when)
                    + ", " + formatDuration(selected.getTotalPlayTime()));
        }
        statusRight.setText(ModrinthStrings.get("instances.total-playtime",
                formatDuration(manager().getTotalPlayTime())));
    }

    static String formatDuration(long millis) {
        long minutes = millis / 60000L;
        long hours = minutes / 60L;
        minutes %= 60L;
        if (hours > 0L) {
            return ModrinthStrings.get("instances.duration.hm", hours, minutes);
        }
        return ModrinthStrings.get("instances.duration.m", minutes);
    }

    // ------------------------------------------------------------ menus

    private JPopupMenu emptyAreaMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("instances.create", "plus", e -> createInstance()));
        menu.add(menuItem("refresh", "refresh", e -> refresh()));
        return menu;
    }

    private JPopupMenu menuFor(final Instance instance) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("instances.play", "play", e -> play(instance)));
        menu.add(menuItem("instances.edit", "pencil", e -> edit(instance)));
        menu.addSeparator();
        menu.add(menuItem("instances.change-icon", "cube", e -> changeIcon(instance)));
        menu.add(menuItem("instances.group", "bars", e -> changeGroup(instance)));
        menu.add(menuItem("instances.open-folder", "folder-open", e -> openFolder(instance)));
        menu.add(menuItem("instances.export", "share", e -> export(instance)));
        menu.add(menuItem("instances.duplicate", "plus-square", e -> duplicate(instance)));
        menu.add(menuItem("instances.rename", "pencil-square", e -> rename(instance)));
        menu.add(menuItem("instances.shortcut", "external-link", e -> createShortcut(instance)));
        menu.addSeparator();
        menu.add(menuItem("instances.delete", "trash", e -> delete(instance)));
        menu.addSeparator();
        menu.add(menuItem("instances.create", "plus", e -> createInstance()));
        return menu;
    }

    private JMenuItem menuItem(String key, String icon, ActionListener action) {
        JMenuItem item = new JMenuItem(ModrinthStrings.get(key));
        item.setIcon(Images.getIcon16(icon));
        item.addActionListener(action);
        return item;
    }

    private void showFoldersMenu(ActionEvent event) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("instances.folder.instances", "folder-open",
                e -> openFolder(manager().getRoot())));
        menu.add(menuItem("instances.folder.game", "folder-open",
                e -> openFolder(MinecraftUtil.getWorkingDirectory(false))));
        showUnder(menu, event);
    }

    private void showHelpMenu(ActionEvent event) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("instances.help.modrinth", "external-link",
                e -> OS.openLink("https://modrinth.com/")));
        menu.add(menuItem("instances.help.about", "info-circle",
                e -> Alert.showMessage(ModrinthStrings.get("instances.help.about"),
                        BuildConfig.PRODUCT_NAME + " " + LegacyLauncher.getVersion())));
        showUnder(menu, event);
    }

    private static void showUnder(JPopupMenu menu, ActionEvent event) {
        if (event.getSource() instanceof JComponent) {
            JComponent source = (JComponent) event.getSource();
            menu.show(source, 0, source.getHeight());
        }
    }

    // ------------------------------------------------------------ actions

    public void createInstance() {
        NewInstanceDialog dialog = new NewInstanceDialog(SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) {
            return;
        }
        try {
            Instance created = manager().create(dialog.getInstanceName(), dialog.getVersionId());
            refresh();
            select(created);
        } catch (IOException e) {
            log.warn("Could not create an instance", e);
            Alert.showError(ModrinthStrings.get("error.title"),
                    ModrinthStrings.get("instances.error.create") + "\n" + e.getMessage());
        }
    }

    void edit(Instance instance) {
        if (instance != null) {
            pane.openInstanceEditor(instance);
        }
    }

    /**
     * Starts the instance without leaving this screen: the progress bar runs along the
     * bottom of the window and Stop takes over in the sidebar.
     */
    void play(Instance instance) {
        if (instance == null) {
            return;
        }
        pane.defaultScene.loginForm.startInstance(instance);
    }

    void stop() {
        pane.defaultScene.loginForm.stopLauncher();
    }

    void rename(Instance instance) {
        if (instance == null) {
            return;
        }
        String newName = Alert.showInputQuestion(ModrinthStrings.get("instances.rename"),
                ModrinthStrings.get("instances.rename.prompt", instance.getName()));
        if (newName == null || newName.trim().isEmpty()) {
            return;
        }
        try {
            manager().rename(instance, newName);
        } catch (IOException e) {
            log.warn("Could not rename {}", instance, e);
            Alert.showError(ModrinthStrings.get("error.title"), String.valueOf(e.getMessage()));
        }
        refresh();
    }

    void changeIcon(Instance instance) {
        if (instance == null) {
            return;
        }
        String chosen = InstanceIconPicker.pick(SwingUtilities.getWindowAncestor(this), instance.getIcon());
        if (chosen == null) {
            return;
        }
        try {
            manager().setIcon(instance, chosen);
        } catch (IOException e) {
            log.warn("Could not change the icon of {}", instance, e);
            Alert.showError(ModrinthStrings.get("error.title"), String.valueOf(e.getMessage()));
        }
        refresh();
    }

    void changeGroup(Instance instance) {
        if (instance == null) {
            return;
        }
        String group = Alert.showInputQuestion(ModrinthStrings.get("instances.group"),
                ModrinthStrings.get("instances.group.prompt", instance.getName()));
        if (group == null) {
            return;
        }
        try {
            manager().setGroup(instance, group);
        } catch (IOException e) {
            log.warn("Could not change the group of {}", instance, e);
            Alert.showError(ModrinthStrings.get("error.title"), String.valueOf(e.getMessage()));
        }
        refresh();
    }

    void duplicate(final Instance instance) {
        if (instance == null) {
            return;
        }
        String name = Alert.showInputQuestion(ModrinthStrings.get("instances.duplicate"),
                ModrinthStrings.get("instances.duplicate.prompt", instance.getName()));
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        final String newName = name;
        statusLeft.setText(ModrinthStrings.get("loading"));
        AsyncThread.execute(() -> {
            try {
                manager().duplicate(instance, newName);
                SwingUtil.later(this::refresh);
            } catch (IOException e) {
                log.warn("Could not duplicate {}", instance, e);
                SwingUtil.later(() -> Alert.showError(ModrinthStrings.get("error.title"),
                        ModrinthStrings.get("instances.error.duplicate") + "\n" + e.getMessage()));
            }
        });
    }

    void export(final Instance instance) {
        if (instance == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(ModrinthStrings.get("instances.export"));
        chooser.setFileFilter(new FileNameExtensionFilter("ZIP (*.zip)", "zip"));
        chooser.setSelectedFile(new File(instance.getId() + ".zip"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        final File destination = chosen.getName().toLowerCase(Locale.ROOT).endsWith(".zip")
                ? chosen
                : new File(chosen.getParentFile(), chosen.getName() + ".zip");

        statusLeft.setText(ModrinthStrings.get("loading"));
        AsyncThread.execute(() -> {
            try {
                manager().export(instance, destination);
                SwingUtil.later(() -> statusLeft.setText(
                        ModrinthStrings.get("instances.exported", destination)));
            } catch (IOException e) {
                log.warn("Could not export {}", instance, e);
                SwingUtil.later(() -> Alert.showError(ModrinthStrings.get("error.title"),
                        ModrinthStrings.get("instances.error.export") + "\n" + e.getMessage()));
            }
        });
    }

    void delete(Instance instance) {
        if (instance == null) {
            return;
        }
        if (!Alert.showQuestion(ModrinthStrings.get("instances.delete"),
                ModrinthStrings.get("instances.confirm.delete", instance.getName()))) {
            return;
        }
        try {
            manager().delete(instance);
            if (selected != null && selected.getId().equals(instance.getId())) {
                selected = null;
            }
        } catch (IOException e) {
            log.warn("Could not delete {}", instance, e);
            Alert.showError(ModrinthStrings.get("error.title"),
                    ModrinthStrings.get("instances.error.delete") + "\n" + e.getMessage());
        }
        refresh();
    }

    void openFolder(Instance instance) {
        if (instance != null) {
            openFolder(instance.getGameDir());
        }
    }

    private void openFolder(final File dir) {
        AsyncThread.execute(() -> {
            dir.mkdirs();
            OS.openFolder(dir);
        });
    }

    /**
     * Writes a small script that starts this instance directly.
     */
    void createShortcut(Instance instance) {
        if (instance == null) {
            return;
        }
        File launcherExe = InstanceShortcuts.findLauncherExecutable();
        if (launcherExe == null) {
            Alert.showError(ModrinthStrings.get("error.title"),
                    ModrinthStrings.get("instances.error.shortcut-target"));
            return;
        }

        JFileChooser chooser = new JFileChooser(InstanceShortcuts.defaultShortcutDir());
        chooser.setDialogTitle(ModrinthStrings.get("instances.shortcut"));
        chooser.setSelectedFile(new File(InstanceShortcuts.suggestedName(instance)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            File written = InstanceShortcuts.write(chooser.getSelectedFile(), launcherExe, instance);
            statusLeft.setText(ModrinthStrings.get("instances.shortcut-created", written));
        } catch (IOException e) {
            log.warn("Could not create a shortcut for {}", instance, e);
            Alert.showError(ModrinthStrings.get("error.title"),
                    ModrinthStrings.get("instances.error.shortcut") + "\n" + e.getMessage());
        }
    }

    private void openLauncherSettings() {
        pane.openDefaultScene();
        pane.defaultScene.setSidePanel(DefaultScene.SidePanel.SETTINGS);
    }

    @Override
    public void updateLocale() {
        for (Map.Entry<String, JButton> entry : toolbarButtons.entrySet()) {
            entry.getValue().setText(ModrinthStrings.get(entry.getKey()));
        }
        updateAccountButton();
        sidebar.updateLocale();
        refresh();
    }
}
