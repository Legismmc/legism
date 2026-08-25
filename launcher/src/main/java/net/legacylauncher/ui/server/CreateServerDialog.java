package net.legacylauncher.ui.server;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.server.ServerCore;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Collections;
import java.util.List;

/**
 * Asks for what a new local server needs: a name, a core, one of that core's published
 * Minecraft versions, and how much RAM to give it - mirrors {@code NewInstanceDialog}'s
 * shape, with the core standing in for the mod loader.
 */
@Slf4j
public class CreateServerDialog extends JDialog {
    private final JTextField nameField = new JTextField();
    private final JComboBox<ServerCore> coreBox = new JComboBox<>(ServerCore.values());
    private final JComboBox<String> versionBox = new JComboBox<>();
    private final JSpinner ramSpinner;
    private final JButton createButton;

    private boolean confirmed;

    public CreateServerDialog(Window owner) {
        super(owner, ModrinthStrings.get("server.new.title"), ModalityType.APPLICATION_MODAL);
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
        form.add(new JLabel(ModrinthStrings.get("server.new.name") + ":"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        nameField.setColumns(24);
        form.add(nameField, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        form.add(new JLabel(ModrinthStrings.get("server.new.core") + ":"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        coreBox.addActionListener(e -> loadVersions());
        form.add(coreBox, c);

        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        form.add(new JLabel(ModrinthStrings.get("server.new.version") + ":"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        versionBox.setEnabled(false);
        form.add(versionBox, c);

        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 0;
        form.add(new JLabel(ModrinthStrings.get("server.new.ram") + ":"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        long totalRam = Math.max(OS.Arch.TOTAL_RAM_MB, 1024L);
        long initial = Math.min(2048L, totalRam);
        ramSpinner = new JSpinner(new SpinnerNumberModel(initial, 512L, totalRam, 256L));
        form.add(ramSpinner, c);

        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(6), SwingUtil.magnify(8)));
        JButton cancel = new JButton(ModrinthStrings.get("server.new.cancel"));
        cancel.addActionListener(e -> dispose());
        buttons.add(cancel);

        createButton = new JButton(ModrinthStrings.get("server.new.create"));
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
        if (getServerName().isEmpty() || getVersion().isEmpty()) {
            return;
        }
        confirmed = true;
        dispose();
    }

    private void loadVersions() {
        ServerCore core = getCore();
        versionBox.setModel(new DefaultComboBoxModel<>(
                new String[]{ModrinthStrings.get("server.new.versions-loading")}));
        versionBox.setEnabled(false);
        AsyncThread.execute(() -> {
            List<String> versions;
            try {
                versions = core.fetchVersions();
            } catch (Exception e) {
                log.warn("Could not list versions for {}", core, e);
                versions = Collections.emptyList();
            }
            List<String> finalVersions = versions;
            SwingUtil.later(() -> {
                if (getCore() != core) {
                    return; // the user picked another core while this was loading
                }
                versionBox.setModel(new DefaultComboBoxModel<>(finalVersions.toArray(new String[0])));
                versionBox.setEnabled(!finalVersions.isEmpty());
                if (!finalVersions.isEmpty()) {
                    versionBox.setSelectedIndex(0);
                }
            });
        });
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getServerName() {
        return nameField.getText().trim();
    }

    public ServerCore getCore() {
        Object selected = coreBox.getSelectedItem();
        return selected instanceof ServerCore ? (ServerCore) selected : ServerCore.VANILLA;
    }

    public String getVersion() {
        Object selected = versionBox.getSelectedItem();
        return selected == null || !versionBox.isEnabled() ? "" : selected.toString();
    }

    public String getXmx() {
        return String.valueOf(((Number) ramSpinner.getValue()).longValue());
    }
}
