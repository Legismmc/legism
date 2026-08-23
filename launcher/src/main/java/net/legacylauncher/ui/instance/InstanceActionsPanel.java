package net.legacylauncher.ui.instance;

import net.legacylauncher.instance.Instance;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * The column on the right of the instance screen: the selected instance's icon and name,
 * then everything that can be done to it, one action per row.
 * <p>
 * With nothing selected the column keeps its width but shows a hint instead, so the grid
 * beside it does not jump around as the selection changes.
 */
public class InstanceActionsPanel extends JPanel {
    static final int WIDTH = 200;

    private final JLabel iconLabel = new JLabel();
    private final JLabel nameLabel = new JLabel();
    private final JLabel versionLabel = new JLabel();
    private final JPanel actions = new JPanel();
    private final JLabel emptyHint = new JLabel();

    private final List<ActionRow> rows = new ArrayList<>();

    private Instance instance;

    public InstanceActionsPanel(InstancesPanel owner) {
        setOpaque(false);
        setLayout(new BorderLayout(0, SwingUtil.magnify(8)));
        setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(6), SwingUtil.magnify(10),
                SwingUtil.magnify(6), SwingUtil.magnify(4)));
        setPreferredSize(new Dimension(SwingUtil.magnify(WIDTH), 0));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));

        iconLabel.setAlignmentX(CENTER_ALIGNMENT);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        head.add(iconLabel);
        head.add(Box.createVerticalStrut(SwingUtil.magnify(6)));

        nameLabel.setAlignmentX(CENTER_ALIGNMENT);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        head.add(nameLabel);

        versionLabel.setAlignmentX(CENTER_ALIGNMENT);
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        versionLabel.setEnabled(false);
        head.add(versionLabel);

        head.add(Box.createVerticalStrut(SwingUtil.magnify(8)));
        JSeparator separator = new JSeparator();
        separator.setAlignmentX(CENTER_ALIGNMENT);
        head.add(separator);

        add(head, BorderLayout.NORTH);

        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));

        row("instances.play", "play", e -> owner.play(instance));
        row("instances.stop", "remove", e -> owner.stop());
        row("instances.edit", "pencil", e -> owner.edit(instance));
        row("instances.group", "bars", e -> owner.changeGroup(instance));
        row("instances.open-folder", "folder-open", e -> owner.openFolder(instance));
        row("instances.export", "share", e -> owner.export(instance));
        row("instances.duplicate", "plus-square", e -> owner.duplicate(instance));
        row("instances.delete", "trash", e -> owner.delete(instance));
        row("instances.shortcut", "external-link", e -> owner.createShortcut(instance));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(actions, BorderLayout.NORTH);

        emptyHint.setText(ModrinthStrings.get("instances.select-hint"));
        emptyHint.setHorizontalAlignment(SwingConstants.CENTER);
        emptyHint.setEnabled(false);
        wrapper.add(emptyHint, BorderLayout.CENTER);

        add(wrapper, BorderLayout.CENTER);

        setInstance(null, false);
    }

    private void row(String key, String icon, ActionListener action) {
        JButton button = new JButton(ModrinthStrings.get(key));
        button.setIcon(Images.getIcon16(icon));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        button.addActionListener(action);
        actions.add(button);
        actions.add(Box.createVerticalStrut(SwingUtil.magnify(2)));
        rows.add(new ActionRow(key, button));
    }

    /**
     * @param running whether the game is currently running from this instance, which is
     *                the only time Stop does anything
     */
    public void setInstance(Instance instance, boolean running) {
        this.instance = instance;

        boolean has = instance != null;
        for (Component component : actions.getComponents()) {
            component.setVisible(has);
        }
        actions.setVisible(has);
        emptyHint.setVisible(!has);

        if (!has) {
            iconLabel.setIcon(null);
            nameLabel.setText("");
            versionLabel.setText("");
            revalidate();
            repaint();
            return;
        }

        iconLabel.setIcon(InstanceTile.buildIcon(SwingUtil.magnify(64)).getIcon());
        nameLabel.setText(instance.getName());
        versionLabel.setText(instance.getVersionId());

        for (ActionRow row : rows) {
            if (row.key.equals("instances.stop")) {
                row.button.setEnabled(running);
            } else if (row.key.equals("instances.play")) {
                row.button.setEnabled(!running);
            }
        }

        revalidate();
        repaint();
    }

    public void updateLocale() {
        for (ActionRow row : rows) {
            row.button.setText(ModrinthStrings.get(row.key));
        }
        emptyHint.setText(ModrinthStrings.get("instances.select-hint"));
    }

    private static class ActionRow {
        final String key;
        final JButton button;

        ActionRow(String key, JButton button) {
            this.key = key;
            this.button = button;
        }
    }
}
