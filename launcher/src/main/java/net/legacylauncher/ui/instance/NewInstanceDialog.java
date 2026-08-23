package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.modrinth.ModLoader;
import net.legacylauncher.modrinth.ModTarget;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;
import net.minecraft.launcher.updater.VersionSyncInfo;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Asks for what an instance needs: a name, a Minecraft version, and then a mod loader -
 * in that order, because which loaders exist depends on the version.
 * <p>
 * Both lists are fixed choices rather than free text: only a version the launcher can
 * actually install is worth offering.
 */
@Slf4j
public class NewInstanceDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final JComboBox<String> versionBox = new JComboBox<>();
    private final JComboBox<LoaderOption> loaderBox = new JComboBox<>();
    private final JButton createButton;

    /**
     * Game version to the launcher version ids that provide it, keyed by mod loader.
     * Iteration order follows the version manager, which lists the newest first.
     */
    private final Map<String, Map<ModLoader, String>> byVersion = new LinkedHashMap<>();

    private boolean confirmed;

    public NewInstanceDialog(Window owner) {
        super(owner, ModrinthStrings.get("instances.new.title"), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(12), SwingUtil.magnify(12),
                SwingUtil.magnify(12), SwingUtil.magnify(12)));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(SwingUtil.magnify(4), SwingUtil.magnify(4),
                SwingUtil.magnify(4), SwingUtil.magnify(4));
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel(ModrinthStrings.get("instances.new.name") + ":"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        nameField.setColumns(24);
        form.add(nameField, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        form.add(new JLabel(ModrinthStrings.get("instances.new.version") + ":"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        versionBox.setModel(new DefaultComboBoxModel<>(
                new String[]{ModrinthStrings.get("instances.versions-loading")}));
        versionBox.setEnabled(false);
        versionBox.addActionListener(e -> refreshLoaders());
        form.add(versionBox, c);

        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        form.add(new JLabel(ModrinthStrings.get("instances.new.loader") + ":"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        loaderBox.setEnabled(false);
        form.add(loaderBox, c);

        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(6), SwingUtil.magnify(8)));
        JButton cancel = new JButton(ModrinthStrings.get("instances.new.cancel"));
        cancel.addActionListener(e -> dispose());
        buttons.add(cancel);

        createButton = new JButton(ModrinthStrings.get("instances.new.create"));
        createButton.addActionListener(e -> confirm());
        buttons.add(createButton);
        getRootPane().setDefaultButton(createButton);
        add(buttons, BorderLayout.SOUTH);

        nameField.addActionListener(e -> confirm());

        loadVersions();

        pack();
        setMinimumSize(new Dimension(SwingUtil.magnify(460), getHeight()));
        setLocationRelativeTo(owner);
    }

    private void confirm() {
        if (getInstanceName().isEmpty() || getVersionId().isEmpty()) {
            return;
        }
        confirmed = true;
        dispose();
    }

    /**
     * Indexes the launcher's version list off the Swing thread: the version manager may
     * still be refreshing when the dialog opens.
     */
    private void loadVersions() {
        AsyncThread.execute(() -> {
            final Map<String, Map<ModLoader, String>> index = new LinkedHashMap<>();
            try {
                for (VersionSyncInfo info : LegacyLauncher.getInstance()
                        .getVersionManager().getVersions(false)) {
                    String id = info.getID();
                    String gameVersion = ModTarget.extractGameVersion(id);
                    if (gameVersion == null) {
                        continue;
                    }
                    ModLoader loader = ModLoader.detect(id);
                    if (loader == null && !id.equals(gameVersion)) {
                        // OptiFine, LiteLoader and friends: not vanilla, and not a loader
                        // this launcher installs mods for
                        continue;
                    }
                    Map<ModLoader, String> loaders = index.get(gameVersion);
                    if (loaders == null) {
                        loaders = new LinkedHashMap<>();
                        index.put(gameVersion, loaders);
                    }
                    // the list is newest first, so the first id wins
                    if (!loaders.containsKey(loader)) {
                        loaders.put(loader, id);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Could not list versions for the new instance dialog", e);
            }
            SwingUtil.later(() -> {
                byVersion.clear();
                byVersion.putAll(index);
                versionBox.setModel(new DefaultComboBoxModel<>(
                        byVersion.keySet().toArray(new String[0])));
                versionBox.setEnabled(!byVersion.isEmpty());
                if (!byVersion.isEmpty()) {
                    versionBox.setSelectedIndex(0);
                }
                refreshLoaders();
            });
        });
    }

    /**
     * Rebuilds the loader list for the chosen version. A version with no modded build in
     * the launcher's list simply offers none.
     */
    private void refreshLoaders() {
        Map<ModLoader, String> loaders = byVersion.get(selectedGameVersion());
        List<LoaderOption> options = new ArrayList<>();
        if (loaders != null) {
            if (loaders.containsKey(null)) {
                options.add(new LoaderOption(null, loaders.get(null)));
            }
            for (ModLoader loader : ModLoader.values()) {
                String id = loaders.get(loader);
                if (id != null) {
                    options.add(new LoaderOption(loader, id));
                }
            }
        }
        loaderBox.setModel(new DefaultComboBoxModel<>(options.toArray(new LoaderOption[0])));
        loaderBox.setEnabled(!options.isEmpty());
        if (!options.isEmpty()) {
            loaderBox.setSelectedIndex(0);
        }
    }

    private String selectedGameVersion() {
        Object selected = versionBox.getSelectedItem();
        return selected == null ? "" : selected.toString();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getInstanceName() {
        return nameField.getText().trim();
    }

    /**
     * The launcher version id the chosen version and loader resolve to.
     */
    public String getVersionId() {
        Object selected = loaderBox.getSelectedItem();
        return selected instanceof LoaderOption ? ((LoaderOption) selected).versionId : "";
    }

    /**
     * One entry of the loader list: what to show, and which launcher version it starts.
     */
    private static class LoaderOption {
        final ModLoader loader;
        final String versionId;

        LoaderOption(ModLoader loader, String versionId) {
            this.loader = loader;
            this.versionId = versionId;
        }

        @Override
        public String toString() {
            return loader == null
                    ? ModrinthStrings.get("instances.new.loader.none")
                    : loader.getDisplayName();
        }
    }
}
