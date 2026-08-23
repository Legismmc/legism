package net.legacylauncher.ui.scenes;

import net.legacylauncher.ui.MainPane;
import net.legacylauncher.ui.instance.InstanceEditPanel;
import net.legacylauncher.util.SwingUtil;


/**
 * Full-screen scene holding the instance editor.
 */
public class InstanceEditScene extends PseudoScene {
    private static final long serialVersionUID = 1L;

    private static final int MARGIN = 12;

    public final InstanceEditPanel panel;

    public InstanceEditScene(MainPane main) {
        super(main);
        panel = new InstanceEditPanel(main);
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
