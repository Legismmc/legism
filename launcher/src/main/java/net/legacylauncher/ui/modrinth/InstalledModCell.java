package net.legacylauncher.ui.modrinth;

import net.legacylauncher.modrinth.InstalledMod;
import net.legacylauncher.modrinth.ModInstaller;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * One jar in the mods directory, with the buttons to disable or delete it.
 */
public class InstalledModCell extends JPanel {

    public InstalledModCell(ModrinthPanel panel, InstalledMod mod) {
        setLayout(new BorderLayout(SwingUtil.magnify(10), 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(6), SwingUtil.magnify(4),
                SwingUtil.magnify(6), SwingUtil.magnify(4)));
        setAlignmentX(LEFT_ALIGNMENT);

        JLabel icon = new JLabel(Images.getIcon24("puzzle-piece"));
        icon.setEnabled(mod.isEnabled());
        add(icon, BorderLayout.WEST);

        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);

        JLabel name = new JLabel(mod.getDisplayName());
        name.setEnabled(mod.isEnabled());
        text.add(name, BorderLayout.NORTH);

        StringBuilder meta = new StringBuilder(ModInstaller.formatSize(mod.getSize()));
        if (!mod.isEnabled()) {
            meta.append("  ·  ").append(ModrinthStrings.get("disable"));
        }
        JLabel metaLabel = new JLabel(meta.toString());
        metaLabel.setEnabled(false);
        text.add(metaLabel, BorderLayout.SOUTH);

        add(text, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(4), 0));
        buttons.setOpaque(false);

        JButton toggle = new JButton(ModrinthStrings.get(mod.isEnabled() ? "disable" : "enable"));
        toggle.setIcon(Images.getIcon16(mod.isEnabled() ? "eye-slash" : "eye"));
        toggle.addActionListener(e -> panel.toggleInstalled(mod));
        buttons.add(toggle);

        JButton delete = new JButton();
        delete.setIcon(Images.getIcon16("trash"));
        delete.setToolTipText(ModrinthStrings.get("delete"));
        delete.addActionListener(e -> panel.deleteInstalled(mod));
        buttons.add(delete);

        add(buttons, BorderLayout.EAST);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
