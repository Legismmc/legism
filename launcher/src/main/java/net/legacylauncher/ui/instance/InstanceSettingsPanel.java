package net.legacylauncher.ui.instance;

import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.settings.MemorySlider;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.SwingUtil;
import net.minecraft.launcher.updater.VersionSyncInfo;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * What used to be one memory setting for the whole launcher now lives here, one instance
 * at a time - a modpack that needs 8 GiB shouldn't force that on every other instance too.
 */
public class InstanceSettingsPanel extends BackdropPanel {
    private final Supplier<Instance> instanceSource;
    private final MemorySlider memorySlider;
    private final JLabel folderLabel = new JLabel();
    private final JLabel status = new JLabel();

    public InstanceSettingsPanel(Supplier<Instance> instanceSource) {
        this.instanceSource = instanceSource;
        setVgap(SwingUtil.magnify(10));

        memorySlider = new MemorySlider(LegacyLauncher.getInstance().getMemoryAllocationService());

        JLabel memoryTitle = new JLabel(ModrinthStrings.get("instance.settings.memory"));
        memoryTitle.setFont(memoryTitle.getFont().deriveFont(Font.BOLD));
        memoryTitle.setAlignmentX(LEFT_ALIGNMENT);
        memorySlider.setAlignmentX(LEFT_ALIGNMENT);

        JLabel folderTitle = new JLabel(ModrinthStrings.get("instance.settings.folder"));
        folderTitle.setFont(folderTitle.getFont().deriveFont(Font.BOLD));
        folderTitle.setAlignmentX(LEFT_ALIGNMENT);
        folderLabel.setEnabled(false);
        folderLabel.setAlignmentX(LEFT_ALIGNMENT);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(memoryTitle);
        center.add(Box.createVerticalStrut(SwingUtil.magnify(4)));
        center.add(memorySlider);
        center.add(Box.createVerticalStrut(SwingUtil.magnify(18)));
        center.add(folderTitle);
        center.add(Box.createVerticalStrut(SwingUtil.magnify(4)));
        center.add(folderLabel);
        setCenter(center);

        JButton saveButton = new JButton(ModrinthStrings.get("instance.settings.save"));
        saveButton.setIcon(Images.getIcon16("save"));
        saveButton.addActionListener(e -> save());

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(saveButton, BorderLayout.WEST);
        status.setEnabled(false);
        south.add(status, BorderLayout.EAST);
        setSouth(south);
    }

    public void onShown() {
        Instance instance = instanceSource.get();
        if (instance == null) {
            return;
        }
        status.setText("");
        folderLabel.setText(instance.getGameDir().getAbsolutePath());

        VersionSyncInfo syncInfo = LegacyLauncher.getInstance().getVersionManager()
                .getVersionSyncInfo(instance.getVersionId());
        memorySlider.setTargetVersion(syncInfo);
        memorySlider.setSettingsValue(instance.getXmx() == null ? "auto" : instance.getXmx());
    }

    private void save() {
        Instance instance = instanceSource.get();
        if (instance == null) {
            return;
        }
        if (!memorySlider.isValueValid()) {
            status.setText(ModrinthStrings.get("instance.settings.invalid"));
            return;
        }
        instance.setXmx(memorySlider.getSettingsValue());
        try {
            LegacyLauncher.getInstance().getInstanceManager().save(instance);
            status.setText(ModrinthStrings.get("instance.settings.saved"));
        } catch (IOException e) {
            status.setText(ModrinthStrings.get("error.title") + ": " + e.getMessage());
        }
    }
}
