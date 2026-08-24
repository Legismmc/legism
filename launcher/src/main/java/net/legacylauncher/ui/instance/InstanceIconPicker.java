package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.instance.InstanceManager;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.explorer.ImageFilePreview;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A small grid of the built-in instance icons, plus an option to upload a picture of your
 * own. Picking a built-in one closes the dialog right away - a separate OK button would
 * only slow down a choice this small down to make; uploading a file naturally needs its own
 * chooser dialog first.
 */
@Slf4j
final class InstanceIconPicker extends JDialog {

    private InstanceIconPicker(Window owner, Instance instance, InstanceManager manager) {
        super(owner, ModrinthStrings.get("instances.change-icon"), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        String current = instance.getIcon();
        List<String> ids = InstanceIcons.ids();
        JPanel grid = new JPanel(new GridLayout(0, 5, SwingUtil.magnify(6), SwingUtil.magnify(6)));
        grid.setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(12), SwingUtil.magnify(12),
                SwingUtil.magnify(6), SwingUtil.magnify(12)));

        int previewSize = SwingUtil.magnify(40);
        Color highlight = InstanceTile.accentColor();
        Color none = new Color(0, 0, 0, 0);
        boolean hasCustom = instance.getCustomIconFile() != null && instance.getCustomIconFile().isFile();
        for (final String id : ids) {
            JButton button = new JButton(InstanceIcons.getIcon(id, previewSize));
            button.setToolTipText(id);
            button.setBorder(BorderFactory.createLineBorder(
                    !hasCustom && id.equals(current) ? highlight : none, SwingUtil.magnify(2)));
            button.addActionListener(e -> {
                try {
                    manager.setIcon(instance, id);
                } catch (IOException ex) {
                    log.warn("Could not change the icon of {}", instance, ex);
                    Alert.showError(ModrinthStrings.get("error.title"), String.valueOf(ex.getMessage()));
                }
                dispose();
            });
            grid.add(button);
        }

        JButton upload = new JButton(ModrinthStrings.get("instances.change-icon.upload"));
        upload.addActionListener(e -> {
            File picked = pickImage(this);
            if (picked == null) {
                return;
            }
            try {
                manager.setCustomIcon(instance, picked);
            } catch (IOException ex) {
                log.warn("Could not set a custom icon for {}", instance, ex);
                Alert.showError(ModrinthStrings.get("error.title"), String.valueOf(ex.getMessage()));
            }
            dispose();
        });
        JPanel uploadRow = new JPanel(new BorderLayout());
        uploadRow.setBorder(BorderFactory.createEmptyBorder(
                0, SwingUtil.magnify(12), SwingUtil.magnify(12), SwingUtil.magnify(12)));
        uploadRow.add(upload, BorderLayout.CENTER);

        add(grid, BorderLayout.CENTER);
        add(uploadRow, BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private static File pickImage(Window owner) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(ModrinthStrings.get("instances.change-icon.upload"));
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Images (*.png, *.jpg, *.jpeg, *.gif, *.webp)", "png", "jpg", "jpeg", "gif", "webp"));
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setAccessory(new ImageFilePreview(chooser));
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile();
    }

    static void show(Window owner, Instance instance, InstanceManager manager) {
        new InstanceIconPicker(owner, instance, manager).setVisible(true);
    }
}
