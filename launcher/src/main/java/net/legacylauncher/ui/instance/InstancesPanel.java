package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.configuration.BuildConfig;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.instance.InstanceManager;
import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.managers.ProfileManager;
import net.legacylauncher.managers.ProfileManagerListener;
import net.legacylauncher.minecraft.auth.Account;
import net.legacylauncher.minecraft.auth.AuthenticatorDatabase;
import net.legacylauncher.ui.MainPane;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.loc.LocalizableComponent;
import net.legacylauncher.ui.modrinth.ModpackBrowserPanel;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.scenes.AccountManagerScene;
import net.legacylauncher.ui.scenes.DefaultScene;
import net.legacylauncher.ui.settings.SettingsPanel;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.MinecraftUtil;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;
import net.legacylauncher.user.User;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
    private final JButton updateButton = new JButton();
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
        installAccountShortcuts();

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
        left.add(toolbarButton("instances.import", "download", this::showImportMenu));
        left.add(toolbarButton("server.title", "plug", e -> openServerHosting()));
        left.add(toolbarButton("instances.folders", "folder-open", this::showFoldersMenu));
        left.add(toolbarButton("instances.settings", "gear", e -> openLauncherSettings()));
        left.add(toolbarButton("instances.help", "question", this::showHelpMenu));
        left.add(toolbarIconButton("refresh", "refresh", e -> refresh()));
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(4), SwingUtil.magnify(2)));
        right.setOpaque(false);
        updateButton.setIcon(tintGreen(Images.getIcon16("download")));
        updateButton.setVisible(false);
        right.add(updateButton);
        accountButton = new JButton();
        accountButton.setIcon(Images.getIcon16("user-circle-o"));
        accountButton.addActionListener(this::showAccountMenu);
        right.add(accountButton);
        bar.add(right, BorderLayout.EAST);

        updateAccountButton();

        return bar;
    }

    /**
     * Lights up a small green "update available" icon in the toolbar - no label, so it
     * cannot push the account button off the edge the way it did with one. This screen,
     * not {@code DefaultScene}, is what the user actually sees on launch - a notification
     * posted to {@code DefaultScene.notificationPanel} the way other one-off startup
     * notices are would never be seen at all.
     */
    public void showUpdateAvailable(String tag, Runnable onClick) {
        updateButton.setToolTipText(ModrinthStrings.get("instances.update-available", tag));
        updateButton.addActionListener(e -> onClick.run());
        updateButton.setVisible(true);
    }

    /**
     * A green-tinted copy of an otherwise plain toolbar icon, for the one button that
     * needs to catch the eye - a color swap on the recognisable "download" icon rather
     * than a whole separate asset.
     */
    private static ImageIcon tintGreen(Icon source) {
        int width = source.getIconWidth();
        int height = source.getIconHeight();
        BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buffer.createGraphics();
        source.paintIcon(null, g, 0, 0);
        g.dispose();

        int green = new Color(0x22, 0xC5, 0x5E).getRGB() & 0x00ffffff;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int alpha = buffer.getRGB(x, y) & 0xff000000;
                if (alpha != 0) {
                    buffer.setRGB(x, y, alpha | green);
                }
            }
        }
        return new ImageIcon(buffer);
    }

    /**
     * Shows who is signed in, falling back to the generic caption when nobody is. The
     * account type comes along because the same nickname can exist twice under different
     * kinds of account.
     */
    private void updateAccountButton() {
        Account<? extends User> account = pane.defaultScene.loginForm.accounts.getAccount();
        if (account == null) {
            accountButton.setIcon(Images.getIcon16("user-circle-o"));
            accountButton.setText(ModrinthStrings.get("instances.accounts"));
            accountButton.setToolTipText(ModrinthStrings.get("instances.accounts"));
            return;
        }
        accountButton.setIcon(accountTypeIcon(account.getType()));
        accountButton.setText(account.getDisplayName());
        accountButton.setToolTipText(account.getDisplayName() + " ["
                + account.getType().toString().toLowerCase(Locale.ROOT) + "]");
    }

    /**
     * The same per-service icons {@link net.legacylauncher.ui.swing.AccountCellRenderer}
     * draws in the account list, so the toolbar shows who you're signed in as - Ely.by,
     * Microsoft or a plain nickname - without having to open that screen to check.
     */
    private static Icon accountTypeIcon(Account.AccountType type) {
        switch (type) {
            case ELY:
            case ELY_LEGACY:
                return Images.getIcon16("logo-ely");
            case MINECRAFT:
                return Images.getIcon16("logo-microsoft");
            case MOJANG:
                return Images.getIcon16("logo-mojang");
            default:
                return Images.getIcon16("user-circle-o");
        }
    }

    private JButton toolbarButton(String key, String icon, ActionListener action) {
        JButton button = new JButton(ModrinthStrings.get(key));
        button.setIcon(Images.getIcon16(icon));
        button.addActionListener(action);
        toolbarButtons.put(key, button);
        return button;
    }

    /**
     * Icon only, no label - for a toolbar that was starting to run out of room. The label
     * still shows up as a tooltip.
     */
    private JButton toolbarIconButton(String key, String icon, ActionListener action) {
        JButton button = new JButton();
        button.setIcon(Images.getIcon16(icon));
        button.setToolTipText(ModrinthStrings.get(key));
        button.addActionListener(action);
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

    /**
     * Every signed-in account, the current one checked off, a number key next to each of
     * the first nine to switch without opening the menu again, and the two actions that
     * are not really about any one account: clearing the active pick, and the full manager.
     */
    private void showAccountMenu(ActionEvent event) {
        JPopupMenu menu = new JPopupMenu();
        List<Account<? extends User>> accounts = new ArrayList<>(
                LegacyLauncher.getInstance().getProfileManager().getAuthDatabase().getAccounts());
        Account<? extends User> current = pane.defaultScene.loginForm.accounts.getAccount();
        Icon check = Images.getIcon16("check-circle-o");

        for (int i = 0; i < accounts.size(); i++) {
            final Account<? extends User> account = accounts.get(i);
            JMenuItem item = new JMenuItem(account.getDisplayName() + " ["
                    + account.getType().toString().toLowerCase(Locale.ROOT) + "]");
            item.setIcon(account.equals(current) ? check : accountTypeIcon(account.getType()));
            if (i < 9) {
                item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.CTRL_DOWN_MASK));
            }
            item.addActionListener(e -> {
                pane.defaultScene.loginForm.accounts.setAccount(account);
                updateAccountButton();
            });
            menu.add(item);
        }

        if (!accounts.isEmpty()) {
            menu.addSeparator();
        }

        JMenuItem clear = menuItem("instances.accounts.clear", "remove", e -> {
            pane.defaultScene.loginForm.accounts.clearAccount();
            updateAccountButton();
        });
        clear.setEnabled(current != null);
        clear.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
        menu.add(clear);

        menu.add(menuItem("instances.accounts.manage", "user-circle-o", e -> openAccountManager()));

        showUnderRightAligned(menu, event);
    }

    /**
     * Backs the same Ctrl+1..Ctrl+9/Ctrl+0 shown as accelerators in {@link #showAccountMenu},
     * so they work without opening that menu first - a standalone {@link JPopupMenu}'s own
     * accelerators are only wired up while it is showing, unlike a real menu bar's.
     */
    private void installAccountShortcuts() {
        InputMap input = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actions = getActionMap();
        for (int i = 0; i < 9; i++) {
            final int index = i;
            String name = "account-switch-" + i;
            input.put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.CTRL_DOWN_MASK), name);
            actions.put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    List<Account<? extends User>> accounts = new ArrayList<>(
                            LegacyLauncher.getInstance().getProfileManager().getAuthDatabase().getAccounts());
                    if (index < accounts.size()) {
                        pane.defaultScene.loginForm.accounts.setAccount(accounts.get(index));
                        updateAccountButton();
                    }
                }
            });
        }
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), "account-clear");
        actions.put("account-clear", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pane.defaultScene.loginForm.accounts.clearAccount();
                updateAccountButton();
            }
        });
    }

    private static void showUnder(JPopupMenu menu, ActionEvent event) {
        if (event.getSource() instanceof JComponent) {
            JComponent source = (JComponent) event.getSource();
            menu.show(source, 0, source.getHeight());
        }
    }

    /**
     * Like {@link #showUnder}, but growing left from the button's right edge instead of
     * right from its left one - for a button that sits flush against the toolbar's own
     * right edge, growing rightward the normal way runs the menu straight off the window.
     */
    private static void showUnderRightAligned(JPopupMenu menu, ActionEvent event) {
        if (event.getSource() instanceof JComponent) {
            JComponent source = (JComponent) event.getSource();
            int x = source.getWidth() - menu.getPreferredSize().width;
            menu.show(source, x, source.getHeight());
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

    /**
     * A modpack can come from a file the user already has or straight out of a library,
     * and the toolbar has no room left for a button each.
     */
    private void showImportMenu(ActionEvent event) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("instances.import.file", "folder-open", e -> importModpack()));
        menu.add(menuItem("instances.import.catalog", "download", e -> openModpackBrowser()));
        showUnder(menu, event);
    }

    /**
     * The modpack catalog, in a window of its own like the other browsers - installing a
     * pack from here creates a whole new instance rather than touching the current one.
     */
    void openModpackBrowser() {
        ModpackBrowserPanel browser = new ModpackBrowserPanel(this::refresh);

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                ModrinthStrings.get("modpack.title"), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.add(browser, BorderLayout.CENTER);

        dialog.setMinimumSize(SwingUtil.magnify(new Dimension(760, 520)));
        dialog.setSize(SwingUtil.magnify(new Dimension(1000, 700)));
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
    }

    /**
     * Imports a modpack as a new instance - either a Modrinth {@code .mrpack}, a
     * CurseForge modpack zip, or this launcher's own exported instance zip. The file is
     * peeked to tell which one it is before committing to any of those paths.
     */
    public void importModpack() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(ModrinthStrings.get("instances.import"));
        chooser.setFileFilter(new FileNameExtensionFilter("Modpack (*.mrpack, *.zip)", "mrpack", "zip"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();

        ModpackImporter.Format format = ModpackImporter.detectFormat(chosen);
        if (format == ModpackImporter.Format.UNKNOWN) {
            Alert.showError(ModrinthStrings.get("error.title"), ModrinthStrings.get("instances.error.import-format"));
            return;
        }

        statusLeft.setText(ModrinthStrings.get("loading"));
        AsyncThread.execute(() -> {
            try {
                Instance imported = ModpackImporter.importAny(chosen, manager(),
                        (message, current, total) -> SwingUtil.later(() ->
                                statusLeft.setText(message + " (" + current + "/" + total + ")")));
                SwingUtil.later(() -> {
                    statusLeft.setText("");
                    refresh();
                    select(imported);
                });
            } catch (IOException e) {
                log.warn("Could not import {}", chosen, e);
                SwingUtil.later(() -> {
                    statusLeft.setText("");
                    Alert.showError(ModrinthStrings.get("error.title"),
                            ModrinthStrings.get("instances.error.import") + "\n" + e.getMessage());
                });
            }
        });
    }

    /**
     * The mod/resourcepack/shader/worlds browser for one instance - same idea as Settings
     * and the account manager, in its own window rather than replacing the instance grid.
     * Unlike those two it's meant to be worked in for a while and browsed against a search
     * list, so this one is resizable and sized generously instead of hugging its content.
     */
    void openServerHosting() {
        net.legacylauncher.ui.server.ServerHostingPanel hostingPanel =
                new net.legacylauncher.ui.server.ServerHostingPanel(pane);

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                ModrinthStrings.get("server.title"), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.add(hostingPanel, BorderLayout.CENTER);

        Dimension size = SwingUtil.magnify(new Dimension(1000, 700));
        dialog.setMinimumSize(SwingUtil.magnify(new Dimension(760, 520)));
        dialog.setSize(size);
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
    }

    void edit(Instance instance) {
        if (instance == null) {
            return;
        }
        InstanceEditPanel editPanel = pane.instanceEditScene.get().panel;
        editPanel.setInstance(instance);

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                ModrinthStrings.get("edit.title", instance.getName() + " — " + instance.getVersionId()),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.add(editPanel, BorderLayout.CENTER);

        Dimension size = SwingUtil.magnify(new Dimension(1000, 700));
        dialog.setMinimumSize(SwingUtil.magnify(new Dimension(760, 520)));
        dialog.setSize(size);
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        editPanel.onShown();
        dialog.setVisible(true);
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
        InstanceIconPicker.show(SwingUtilities.getWindowAncestor(this), instance, manager());
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

    /**
     * Settings used to take over the whole window (switching away from whatever instance
     * grid or editor you had open); showing it in its own window instead means the rest of
     * the launcher stays exactly as you left it underneath.
     */
    private void openLauncherSettings() {
        pane.defaultScene.setSidePanel(DefaultScene.SidePanel.SETTINGS);
        SettingsPanel settingsPanel = pane.defaultScene.settingsForm.get();
        settingsPanel.setVisible(true);

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                ModrinthStrings.get("instances.settings"), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.add(settingsPanel, BorderLayout.CENTER);
        // the panel's own Save/Home buttons still call DefaultScene.setSidePanel(null),
        // which hides it - that's the cue to close the window it's sitting in now
        settingsPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentHidden(java.awt.event.ComponentEvent e) {
                dialog.dispose();
            }
        });
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
        // covers closing via the window's own [x] instead of a button inside the panel
        pane.defaultScene.setSidePanel(null);
    }

    /**
     * Same idea as the settings window: the account manager used to replace the whole
     * window too. It's an absolutely-positioned scene sized for wherever it used to be
     * shown, so instead of letting it stretch to fill this dialog, it keeps the compact
     * size it was designed for and the dialog wraps snugly around just that.
     */
    private void openAccountManager() {
        AccountManagerScene scene = pane.accountManager.get();

        int gap = SwingUtil.magnify(15);
        int width = scene.list.getWidth() + gap + scene.multipane.getWidth();
        int height = Math.max(scene.list.getHeight(), scene.multipane.getHeight());
        scene.setPreferredSize(new Dimension(width, height));
        scene.setSize(width, height);
        scene.list.setLocation(0, 0);
        scene.multipane.setLocation(scene.list.getWidth() + gap, 0);
        scene.multipane.showTip("welcome");

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                ModrinthStrings.get("instances.accounts"), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.add(scene, BorderLayout.CENTER);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
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
