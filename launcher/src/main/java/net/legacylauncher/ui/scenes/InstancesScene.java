package net.legacylauncher.ui.scenes;

import net.legacylauncher.ui.MainPane;
import net.legacylauncher.ui.instance.InstancesPanel;
import net.legacylauncher.util.SwingUtil;

/**
 * Full-screen scene holding the instance manager.
 * <p>
 * Unlike the other scenes this one fills the window rather than floating a card in the
 * middle: it is a workspace, and the grid needs the room.
 */
public class InstancesScene extends PseudoScene {
    private static final long serialVersionUID = 1L;

    private static final int MARGIN = 12;

    public final InstancesPanel panel;

    public InstancesScene(MainPane main) {
        super(main);
        panel = new InstancesPanel(main);
        add(panel);
    }

    @Override
    public void onResize() {
        super.onResize();

        int margin = SwingUtil.magnify(MARGIN);
        panel.setBounds(margin, margin,
                Math.max(getWidth() - margin * 2, 0),
                Math.max(getHeight() - margin * 2, 0));
    }
}
