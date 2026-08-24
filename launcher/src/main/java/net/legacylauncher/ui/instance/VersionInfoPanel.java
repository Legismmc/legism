package net.legacylauncher.ui.instance;

import net.legacylauncher.instance.Instance;
import net.legacylauncher.modrinth.ModLoader;
import net.legacylauncher.modrinth.ModTarget;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.SwingUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;
import java.text.DateFormat;
import java.util.Date;
import java.util.function.Supplier;

/**
 * The facts about this instance that don't change from an editor screen - what it's
 * running and where it lives. Changing the version after the fact isn't offered here: mods
 * already installed for one version rarely survive moving to another.
 */
public class VersionInfoPanel extends BackdropPanel {
    private final Supplier<Instance> instanceSource;
    private final JPanel rows = new JPanel();

    public VersionInfoPanel(Supplier<Instance> instanceSource) {
        this.instanceSource = instanceSource;
        setVgap(SwingUtil.magnify(8));

        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        setCenter(rows);
    }

    public void onShown() {
        rows.removeAll();
        Instance instance = instanceSource.get();
        if (instance != null) {
            String versionId = instance.getVersionId();
            ModLoader loader = ModLoader.detect(versionId);
            String gameVersion = ModTarget.extractGameVersion(versionId);

            addRow("instance.version.minecraft", gameVersion == null ? versionId : gameVersion);
            addRow("instance.version.loader", loader == null
                    ? ModrinthStrings.get("instances.new.loader.none") : loader.getDisplayName());
            addRow("instance.version.id", versionId);
            addRow("instance.version.created", DateFormat.getDateTimeInstance().format(new Date(instance.getCreated())));
            if (instance.getLastPlayed() > 0) {
                addRow("instance.version.last-played", DateFormat.getDateTimeInstance().format(new Date(instance.getLastPlayed())));
            }
            addRow("instance.version.folder", instance.getFolder().getAbsolutePath());
        }
        rows.revalidate();
        rows.repaint();
    }

    private void addRow(String labelKey, String value) {
        JLabel label = new JLabel(ModrinthStrings.get(labelKey) + ":");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setAlignmentX(LEFT_ALIGNMENT);
        rows.add(label);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setAlignmentX(LEFT_ALIGNMENT);
        rows.add(valueLabel);
        rows.add(Box.createVerticalStrut(SwingUtil.magnify(10)));
    }
}
