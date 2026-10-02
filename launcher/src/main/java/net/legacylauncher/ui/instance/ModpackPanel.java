package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.instance.InstanceManager;
import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.modpack.ModpackOrigin;
import net.legacylauncher.modpack.ModpackSource;
import net.legacylauncher.modpack.ModpackSources;
import net.legacylauncher.modpack.PackFormats;
import net.legacylauncher.modpack.PackInstaller;
import net.legacylauncher.modpack.PackPlan;
import net.legacylauncher.modpack.PackVersion;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.modrinth.ModpackInstallDialog;
import net.legacylauncher.ui.modrinth.ModpackSkippedDialog;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;
import org.apache.commons.lang3.StringUtils;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The page of an instance that came from a modpack: which pack and version it is, where it
 * came from, the versions it can move to with what each one changed, and the buttons to
 * move to one - from the library, or from a pack file on disk.
 */
@Slf4j
public class ModpackPanel extends BackdropPanel {
    private final Supplier<Instance> instanceSource;
    private final Consumer<Instance> onUpdated;

    private final JLabel sourceTitle = new JLabel();
    private final JLabel nameValue = new JLabel();
    private final JLabel versionValue = new JLabel();
    private final JLabel originValue = new JLabel();
    private final JComboBox<PackVersion> versionBox = new JComboBox<>();
    private final JButton updateButton = new JButton();
    private final JButton fromFileButton = new JButton();
    private final JEditorPane changelog = new JEditorPane();
    private final JButton reloadButton = new JButton();

    private int generation;

    public ModpackPanel(Supplier<Instance> instanceSource, Consumer<Instance> onUpdated) {
        this.instanceSource = instanceSource;
        this.onUpdated = onUpdated;
        setVgap(SwingUtil.magnify(6));

        sourceTitle.setFont(sourceTitle.getFont().deriveFont(Font.BOLD, sourceTitle.getFont().getSize2D() * 1.3f));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(ModrinthStrings.get("modpack.page.info")),
                BorderFactory.createEmptyBorder(SwingUtil.magnify(4), SwingUtil.magnify(8),
                        SwingUtil.magnify(4), SwingUtil.magnify(8))));
        info.add(row("modpack.page.name", nameValue));
        info.add(row("modpack.page.version", versionValue));
        info.add(row("modpack.page.origin", originValue));
        originValue.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        originValue.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Instance instance = instanceSource.get();
                if (instance != null && instance.getModpack() != null && instance.getModpack().getPageUrl() != null) {
                    OS.openLink(instance.getModpack().getPageUrl());
                }
            }
        });

        versionBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof PackVersion) {
                    PackVersion version = (PackVersion) value;
                    StringBuilder text = new StringBuilder(version.getName());
                    if (version.getGameVersion() != null) {
                        text.append("  —  ").append(version.getGameVersion());
                    }
                    if (!version.isRelease()) {
                        text.append("  [").append(ModrinthStrings.get("modpack.page.beta")).append(']');
                    }
                    if (isCurrent(version)) {
                        text.append("  (").append(ModrinthStrings.get("modpack.page.current")).append(')');
                    }
                    setText(text.toString());
                }
                return this;
            }
        });
        versionBox.addActionListener(e -> loadChangelog());

        updateButton.setText(ModrinthStrings.get("modpack.page.update"));
        updateButton.addActionListener(e -> updateFromLibrary());
        fromFileButton.setText(ModrinthStrings.get("modpack.page.update-file"));
        fromFileButton.addActionListener(e -> updateFromFile());

        JPanel updateRow = new JPanel(new BorderLayout(SwingUtil.magnify(6), 0));
        updateRow.setOpaque(false);
        updateRow.add(new JLabel(ModrinthStrings.get("modpack.page.update-to")), BorderLayout.WEST);
        updateRow.add(versionBox, BorderLayout.CENTER);
        JPanel updateButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(4), 0));
        updateButtons.setOpaque(false);
        updateButtons.add(updateButton);
        updateButtons.add(fromFileButton);
        updateRow.add(updateButtons, BorderLayout.EAST);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        sourceTitle.setAlignmentX(LEFT_ALIGNMENT);
        info.setAlignmentX(LEFT_ALIGNMENT);
        updateRow.setAlignmentX(LEFT_ALIGNMENT);
        JLabel changesLabel = new JLabel(ModrinthStrings.get("modpack.page.changelog"));
        changesLabel.setAlignmentX(LEFT_ALIGNMENT);
        top.add(sourceTitle);
        top.add(javax.swing.Box.createVerticalStrut(SwingUtil.magnify(4)));
        top.add(info);
        top.add(javax.swing.Box.createVerticalStrut(SwingUtil.magnify(8)));
        top.add(updateRow);
        top.add(javax.swing.Box.createVerticalStrut(SwingUtil.magnify(8)));
        top.add(changesLabel);
        setNorth(top);

        changelog.setContentType("text/html");
        changelog.setEditable(false);
        changelog.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        changelog.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                OS.openLink(e.getURL().toString());
            }
        });
        JScrollPane scroll = new JScrollPane(changelog);
        scroll.getVerticalScrollBar().setUnitIncrement(SwingUtil.magnify(16));
        setCenter(scroll);

        reloadButton.setText(ModrinthStrings.get("modpack.page.reload"));
        reloadButton.addActionListener(e -> onShown());
        setSouth(reloadButton);
    }

    private JPanel row(String labelKey, JLabel value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(6), SwingUtil.magnify(2)));
        row.setOpaque(false);
        row.add(new JLabel(ModrinthStrings.get(labelKey) + ":"));
        row.add(value);
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    private boolean isCurrent(PackVersion version) {
        Instance instance = instanceSource.get();
        ModpackOrigin origin = instance == null ? null : instance.getModpack();
        return origin != null && version.getId().equals(origin.getVersionId());
    }

    private ModpackSource source() {
        Instance instance = instanceSource.get();
        ModpackOrigin origin = instance == null ? null : instance.getModpack();
        return origin == null ? null : ModpackSources.byId(origin.getSource());
    }

    public void onShown() {
        Instance instance = instanceSource.get();
        ModpackOrigin origin = instance == null ? null : instance.getModpack();
        if (origin == null) {
            return;
        }
        ModpackSource source = ModpackSources.byId(origin.getSource());
        sourceTitle.setText(source == null ? origin.getSource() : source.getDisplayName());
        nameValue.setText(StringUtils.defaultString(origin.getName()));
        versionValue.setText(StringUtils.defaultString(origin.getVersionName()));
        originValue.setText("<html>" + ModrinthStrings.get("modpack.page.site")
                + " <a href=\"#\">" + (source == null ? origin.getSource() : source.getDisplayName()) + "</a>"
                + " &nbsp;|&nbsp; " + ModrinthStrings.get("modpack.page.project-id") + ": " + escape(origin.getProjectId())
                + (origin.getVersionId() == null ? "" : " &nbsp;|&nbsp; " + ModrinthStrings.get("modpack.page.version-id")
                + ": " + escape(origin.getVersionId())) + "</html>");

        boolean usable = source != null && source.isAvailable();
        updateButton.setEnabled(false);
        fromFileButton.setEnabled(true);
        versionBox.setModel(new DefaultComboBoxModel<>());
        showMessage(ModrinthStrings.get("loading"));
        if (!usable) {
            showMessage(source == null ? ModrinthStrings.get("modpack.page.unknown-source")
                    : ModrinthStrings.get(source.getUnavailableReason()));
            return;
        }

        final int ticket = ++generation;
        final String projectId = origin.getProjectId();
        AsyncThread.execute(() -> {
            List<PackVersion> versions;
            try {
                versions = source.listVersions(projectId);
            } catch (IOException | RuntimeException e) {
                log.warn("Could not list the versions of {}", projectId, e);
                SwingUtil.later(() -> {
                    if (ticket == generation) {
                        showMessage(ModrinthStrings.get("modpack.page.error.versions") + " " + e.getMessage());
                    }
                });
                return;
            }
            SwingUtil.later(() -> {
                if (ticket != generation) {
                    return;
                }
                versionBox.setModel(new DefaultComboBoxModel<>(versions.toArray(new PackVersion[0])));
                updateButton.setEnabled(!versions.isEmpty());
                if (versions.isEmpty()) {
                    showMessage(ModrinthStrings.get("modpack.page.no-versions"));
                } else {
                    versionBox.setSelectedIndex(0);
                    loadChangelog();
                }
            });
        });
    }

    private void loadChangelog() {
        Object selected = versionBox.getSelectedItem();
        ModpackSource source = source();
        Instance instance = instanceSource.get();
        if (!(selected instanceof PackVersion) || source == null || instance == null) {
            return;
        }
        PackVersion version = (PackVersion) selected;
        String projectId = instance.getModpack().getProjectId();
        final int ticket = ++generation;
        showMessage(ModrinthStrings.get("loading"));
        AsyncThread.execute(() -> {
            String html;
            try {
                html = source.getChangelogHtml(projectId, version);
            } catch (IOException | RuntimeException e) {
                log.warn("Could not load the changelog of {} {}", projectId, version.getId(), e);
                html = null;
            }
            final String text = html;
            SwingUtil.later(() -> {
                if (ticket != generation) {
                    return;
                }
                if (text == null) {
                    showMessage(ModrinthStrings.get("modpack.page.no-changelog"));
                } else {
                    try {
                        changelog.setText(text);
                    } catch (RuntimeException e) {
                        // Swing's HTML support is from another century; a page it chokes
                        // on is shown as text rather than not at all
                        changelog.setContentType("text/plain");
                        changelog.setText(text.replaceAll("<[^>]+>", ""));
                        changelog.setContentType("text/html");
                    }
                    changelog.setCaretPosition(0);
                }
            });
        });
    }

    private void showMessage(String message) {
        changelog.setText("<html><body><p>" + escape(message) + "</p></body></html>");
    }

    private void updateFromLibrary() {
        Instance instance = instanceSource.get();
        ModpackSource source = source();
        Object selected = versionBox.getSelectedItem();
        if (instance == null || source == null || !(selected instanceof PackVersion) || refuseWhileRunning(instance)) {
            return;
        }
        PackVersion version = (PackVersion) selected;
        ModpackOrigin origin = instance.getModpack();
        if (!confirm(instance, origin.getVersionName(), version.getName())) {
            return;
        }
        run(instance, listener -> source.prepare(origin.getProjectId(), version, listener),
                origin.withVersion(version.getId(), version.getName()));
    }

    private void updateFromFile() {
        Instance instance = instanceSource.get();
        if (instance == null || instance.getModpack() == null || refuseWhileRunning(instance)) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(ModrinthStrings.get("modpack.page.update-file"));
        chooser.setFileFilter(new FileNameExtensionFilter("Modpack (*.mrpack, *.zip)", "mrpack", "zip"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        ModpackImporter.Format format = ModpackImporter.detectFormat(file);
        if (format != ModpackImporter.Format.MRPACK && format != ModpackImporter.Format.CURSEFORGE) {
            Alert.showError(ModrinthStrings.get("error.title"), ModrinthStrings.get("instances.error.import-format"));
            return;
        }
        if (!confirm(instance, instance.getModpack().getVersionName(), file.getName())) {
            return;
        }
        run(instance, listener -> PackFormats.read(file, listener), null /* filled in from the file */);
    }

    private interface PlanMaker {
        PackPlan make(ModpackImporter.ProgressListener listener) throws IOException;
    }

    /**
     * @param newOrigin what the instance comes from afterwards; {@code null} to keep the
     *                  pack and take the version name from the file
     */
    private void run(Instance instance, PlanMaker maker, ModpackOrigin newOrigin) {
        InstanceManager manager = LegacyLauncher.getInstance().getInstanceManager();
        ModpackInstallDialog progress = new ModpackInstallDialog(this, instance.getName());
        AsyncThread.execute(() -> {
            try {
                PackPlan plan = maker.make(progress);
                ModpackOrigin origin = newOrigin != null ? newOrigin
                        : instance.getModpack().withVersion(null, plan.getVersionName());
                ModpackImporter.Result result = PackInstaller.update(instance, plan, origin, manager, progress);
                progress.done();
                SwingUtil.later(() -> {
                    onUpdated.accept(result.getInstance());
                    onShown();
                    Alert.showMessage(ModrinthStrings.get("modpack.page.updated.title"),
                            ModrinthStrings.get("modpack.page.updated", origin.getVersionName()));
                    ModpackSkippedDialog.showIfNeeded(this, result);
                });
            } catch (ModpackImporter.CancelledException e) {
                progress.done();
            } catch (IOException | RuntimeException e) {
                log.warn("Could not update {}", instance, e);
                progress.done();
                SwingUtil.later(() -> Alert.showError(ModrinthStrings.get("error.title"),
                        ModrinthStrings.get("modpack.page.error.update") + "\n" + e.getMessage()));
            }
        });
        progress.showDialog();
    }

    private boolean refuseWhileRunning(Instance instance) {
        Instance running = LegacyLauncher.getInstance().getInstanceManager().getRunning();
        if (running != null && running.getId().equals(instance.getId())) {
            Alert.showError(ModrinthStrings.get("error.title"), ModrinthStrings.get("modpack.page.running"));
            return true;
        }
        return false;
    }

    private boolean confirm(Instance instance, String from, String to) {
        return JOptionPane.showConfirmDialog(this,
                ModrinthStrings.get("modpack.page.confirm", instance.getName(),
                        StringUtils.defaultString(from, "?"), to),
                ModrinthStrings.get("modpack.page.update"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * @return whether this instance has a modpack page to show at all
     */
    public static boolean applies(Instance instance) {
        return instance != null && instance.getModpack() != null;
    }
}
