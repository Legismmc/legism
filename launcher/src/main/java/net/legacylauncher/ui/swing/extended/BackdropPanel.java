package net.legacylauncher.ui.swing.extended;

import net.legacylauncher.ui.theme.Theme;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A panel that lays a translucent sheet of the theme's panel colour behind its contents.
 * <p>
 * The launcher paints a photograph behind everything, which reads well behind a small
 * login form but not behind a screen full of text. The sheet keeps the wallpaper visible
 * while giving labels something to sit on.
 */
public class BackdropPanel extends BorderPanel {
    private static final int ALPHA = 226;
    private static final int ARC = 10;

    @Override
    protected void paintComponent(Graphics g) {
        Color base = Theme.getTheme().getPanelBackground();
        if (base != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), ALPHA));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
