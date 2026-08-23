package net.legacylauncher.ui.scenes;

import net.legacylauncher.ui.MainPane;
import net.legacylauncher.ui.modrinth.ModrinthPanel;
import net.legacylauncher.util.SwingUtil;

import java.awt.Dimension;

/**
 * Full-screen scene holding the Modrinth mod browser.
 */
public class ModrinthScene extends PseudoScene {
    private static final long serialVersionUID = 1L;

    private static final int MARGIN = 20;

    public final ModrinthPanel panel;

    public ModrinthScene(MainPane main) {
        super(main);
        panel = new ModrinthPanel(main);
        add(panel);
    }

    @Override
    public void onResize() {
        super.onResize();

        int margin = SwingUtil.magnify(MARGIN);
        Dimension preferred = panel.getPreferredSize();

        int width = Math.min(preferred.width, getWidth() - margin * 2);
        int height = Math.min(preferred.height, getHeight() - margin * 2);

        panel.setBounds(
                getWidth() / 2 - width / 2,
                getHeight() / 2 - height / 2,
                Math.max(width, 0),
                Math.max(height, 0)
        );
    }
}
