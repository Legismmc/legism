package net.legacylauncher.ui.instance;

import net.legacylauncher.ui.MainPane;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.loc.LocalizableComponent;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.swing.extended.ExtendedButton;
import net.legacylauncher.ui.swing.extended.ExtendedPanel;
import net.legacylauncher.util.SwingUtil;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The toolbar in the corner of the main screen.
 * <p>
 * Left-clicking opens the instance list; right-clicking goes straight to creating one,
 * which is the shortcut a Prism user reaches for.
 */
public class InstancesToolbar extends ExtendedPanel implements LocalizableComponent {

    private final ExtendedButton instancesButton = new ExtendedButton();

    public InstancesToolbar(MainPane pane) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);

        instancesButton.setIcon(Images.getIcon16("cube"));
        instancesButton.addActionListener(e -> pane.openInstancesScene());
        // ExtendedPanel forwards mouse listeners to its children, so registering here is
        // enough for the button to answer a right-click as well
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e, pane);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e, pane);
            }
        });
        add(instancesButton);

        updateLocale();
    }

    private void maybePopup(MouseEvent e, MainPane pane) {
        if (!e.isPopupTrigger()) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();

        JMenuItem open = new JMenuItem(ModrinthStrings.get("instances.title"));
        open.setIcon(Images.getIcon16("cube"));
        open.addActionListener(a -> pane.openInstancesScene());
        menu.add(open);

        JMenuItem create = new JMenuItem(ModrinthStrings.get("instances.create"));
        create.setIcon(Images.getIcon16("plus"));
        create.addActionListener(a -> pane.openInstancesSceneAndCreate());
        menu.add(create);

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    @Override
    public void updateLocale() {
        instancesButton.setText(ModrinthStrings.get("instances.title"));
        instancesButton.setToolTipText(ModrinthStrings.get("instances.title"));
        setSize(getPreferredSize());
    }

    @Override
    public java.awt.Insets getInsets() {
        return SwingUtil.magnify(super.getInsets());
    }
}
