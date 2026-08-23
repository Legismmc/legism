package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
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
import java.util.List;

/**
 * Asks for the two things an instance needs: a name and a Minecraft version.
 */
@Slf4j
public class NewInstanceDialog extends JDialog {

    private final JTextField nameField = new JTextField();
    private final JComboBox<String> versionBox = new JComboBox<>();
    private final JButton createButton;

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
        versionBox.setEditable(true);
        versionBox.setModel(new DefaultComboBoxModel<>(
                new String[]{ModrinthStrings.get("instances.versions-loading")}));
        versionBox.setEnabled(false);
        form.add(versionBox, c);

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
        setMinimumSize(new Dimension(SwingUtil.magnify(420), getHeight()));
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
     * Fills the version list off the Swing thread: the version manager may still be
     * refreshing when the dialog opens.
     */
    private void loadVersions() {
        AsyncThread.execute(() -> {
            final List<String> ids = new ArrayList<>();
            try {
                for (VersionSyncInfo info : LegacyLauncher.getInstance()
                        .getVersionManager().getVersions(false)) {
                    ids.add(info.getID());
                }
            } catch (RuntimeException e) {
                log.warn("Could not list versions for the new instance dialog", e);
            }
            SwingUtil.later(() -> {
                if (ids.isEmpty()) {
                    versionBox.setModel(new DefaultComboBoxModel<>(new String[0]));
                } else {
                    versionBox.setModel(new DefaultComboBoxModel<>(ids.toArray(new String[0])));
                    versionBox.setSelectedIndex(0);
                }
                versionBox.setEnabled(true);
            });
        });
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getInstanceName() {
        return nameField.getText().trim();
    }

    public String getVersionId() {
        Object selected = versionBox.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }
}
