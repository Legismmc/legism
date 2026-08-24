package net.legacylauncher.ui.instance;

import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.List;

/**
 * A small grid of the built-in instance icons. Picking one closes the dialog right away - a
 * separate OK button would only slow down a choice this small down to make.
 */
final class InstanceIconPicker extends JDialog {
    private String chosen;

    private InstanceIconPicker(Window owner, String current) {
        super(owner, ModrinthStrings.get("instances.change-icon"), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        List<String> ids = InstanceIcons.ids();
        JPanel grid = new JPanel(new GridLayout(0, 5, SwingUtil.magnify(6), SwingUtil.magnify(6)));
        grid.setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(12), SwingUtil.magnify(12),
                SwingUtil.magnify(12), SwingUtil.magnify(12)));

        int previewSize = SwingUtil.magnify(40);
        Color highlight = InstanceTile.accentColor();
        Color none = new Color(0, 0, 0, 0);
        for (final String id : ids) {
            JButton button = new JButton(InstanceIcons.getIcon(id, previewSize));
            button.setToolTipText(id);
            button.setBorder(BorderFactory.createLineBorder(
                    id.equals(current) ? highlight : none, SwingUtil.magnify(2)));
            button.addActionListener(e -> {
                chosen = id;
                dispose();
            });
            grid.add(button);
        }

        add(grid);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /**
     * @return the picked icon id, or {@code null} if the dialog was closed without choosing
     */
    static String pick(Window owner, String current) {
        InstanceIconPicker dialog = new InstanceIconPicker(owner, current);
        dialog.setVisible(true);
        return dialog.chosen;
    }
}
