package net.legacylauncher.ui.instance;

import net.legacylauncher.instance.Instance;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.theme.Theme;
import net.legacylauncher.util.Lazy;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * One instance in the grid: a grass block icon with the name underneath.
 * <p>
 * The selected tile gets the accent bar behind its name, which is what keeps the selection
 * readable over the launcher's photographic background.
 */
public class InstanceTile extends JPanel {
    static final int TILE_WIDTH = 96;
    static final int ICON_SIZE = 56;

    /**
     * The launcher ships a tileable grass texture; a square crop of it makes the block
     * icon without adding another asset.
     */
    private static final Lazy<BufferedImage> GRASS = Lazy.of(() -> {
        BufferedImage full = Images.loadImageByName("grass.png");
        if (full == null) {
            return null;
        }
        int side = Math.min(full.getWidth(), full.getHeight());
        return full.getSubimage(0, 0, side, side);
    });

    private final Instance instance;

    private boolean selected;

    public InstanceTile(Instance instance) {
        this.instance = instance;
        setOpaque(false);
        setLayout(new BorderLayout(0, SwingUtil.magnify(4)));
        setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(6), SwingUtil.magnify(6),
                SwingUtil.magnify(6), SwingUtil.magnify(6)));

        add(buildIcon(SwingUtil.magnify(ICON_SIZE)), BorderLayout.CENTER);

        JLabel nameLabel = new NameLabel(instance.getName());
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setToolTipText(instance.getName() + " — " + instance.getVersionId());
        add(nameLabel, BorderLayout.SOUTH);

        int width = SwingUtil.magnify(TILE_WIDTH);
        Dimension size = new Dimension(width, width + SwingUtil.magnify(14));
        setPreferredSize(size);
        setMaximumSize(size);
        setMinimumSize(size);
    }

    static JLabel buildIcon(int size) {
        JLabel icon = new JLabel();
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        BufferedImage grass = GRASS.get();
        if (grass == null) {
            icon.setIcon(Images.getIcon("cube", size));
        } else {
            // nearest neighbour keeps the pixels crisp instead of smearing the texture
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(grass, 0, 0, size, size, null);
            g.dispose();
            icon.setIcon(new javax.swing.ImageIcon((Image) scaled));
        }
        return icon;
    }

    public Instance getInstance() {
        return instance;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            repaint();
        }
    }

    static Color accentColor() {
        Color accent = UIManager.getColor("List.selectionBackground");
        return accent == null ? new Color(0x2D6BB5) : accent;
    }

    /**
     * The name under the icon, with the selection bar painted behind it.
     */
    private class NameLabel extends JLabel {
        NameLabel(String text) {
            super(text);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (selected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accentColor());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        SwingUtil.magnify(6), SwingUtil.magnify(6));
                g2.dispose();
                setForeground(Color.WHITE);
            } else {
                setForeground(Theme.getTheme().getForeground());
            }
            super.paintComponent(g);
        }
    }
}
