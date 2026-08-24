package net.legacylauncher.ui.instance;

import net.legacylauncher.instance.Instance;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.util.Lazy;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small built-in set of block-like instance icons, so a fresh grid of instances does not
 * all wear the same picture.
 * <p>
 * Every icon but {@code grass} - the launcher's original texture crop - is painted in code:
 * a rounded tile with a diagonal light-to-dark gradient, evoking a block face without
 * reusing any of Mojang's own artwork.
 */
public final class InstanceIcons {
    private static final String DEFAULT_ID = "grass";

    private static final List<String> IDS = new ArrayList<>();
    private static final Map<String, Color> PALETTE = new HashMap<>();
    private static final Map<String, Icon> CACHE = new HashMap<>();

    /**
     * The launcher ships a tileable grass texture; a square crop of it makes the original
     * block icon without adding another asset.
     */
    private static final Lazy<BufferedImage> GRASS = Lazy.of(() -> {
        BufferedImage full = Images.loadImageByName("grass.png");
        if (full == null) {
            return null;
        }
        int side = Math.min(full.getWidth(), full.getHeight());
        return full.getSubimage(0, 0, side, side);
    });

    static {
        register(DEFAULT_ID, null); // the real texture crop, handled separately in build()
        register("stone", new Color(0x8A8A8A));
        register("dirt", new Color(0x8B5A2B));
        register("oak", new Color(0xAB7B4F));
        register("water", new Color(0x3B78C2));
        register("lava", new Color(0xE0611B));
        register("diamond", new Color(0x4FD8D8));
        register("gold", new Color(0xF3C348));
        register("redstone", new Color(0xC62B2B));
        register("lapis", new Color(0x2F53A6));
        register("emerald", new Color(0x2FBF71));
        register("obsidian", new Color(0x362A4D));
        register("netherrack", new Color(0x6E2626));
        register("iron", new Color(0xD8D8D8));
    }

    private InstanceIcons() {
    }

    private static void register(String id, Color color) {
        IDS.add(id);
        if (color != null) {
            PALETTE.put(id, color);
        }
    }

    /**
     * Every icon id, in the order they should be offered in a picker.
     */
    public static List<String> ids() {
        return Collections.unmodifiableList(IDS);
    }

    /**
     * A default that depends on the instance rather than a single fixed picture, so a fresh
     * grid of instances is not one long row of the same icon.
     */
    public static String pickDefault(String seed) {
        if (seed == null || seed.isEmpty()) {
            return DEFAULT_ID;
        }
        int index = Math.floorMod(seed.hashCode(), IDS.size());
        return IDS.get(index);
    }

    /**
     * The icon for one instance: whatever it was explicitly given, or a default picked
     * deterministically from its id.
     */
    public static Icon getIcon(Instance instance, int size) {
        if (instance == null) {
            return getIcon((String) null, size);
        }
        String id = instance.getIcon();
        return getIcon(id == null ? pickDefault(instance.getId()) : id, size);
    }

    public static Icon getIcon(String id, int size) {
        String resolved = id == null ? DEFAULT_ID : id;
        String key = resolved + "@" + size;
        Icon cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Icon built = build(resolved, size);
        CACHE.put(key, built);
        return built;
    }

    private static Icon build(String id, int size) {
        Color color = PALETTE.get(id);
        if (color != null) {
            return new ImageIcon(paint(color, size));
        }

        BufferedImage grass = GRASS.get();
        if (grass == null) {
            return Images.getIcon("cube", size);
        }
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        // nearest neighbour keeps the pixels crisp instead of smearing the texture
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(grass, 0, 0, size, size, null);
        g.dispose();
        return new ImageIcon((Image) scaled);
    }

    private static BufferedImage paint(Color base, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // a smooth diagonal gradient reads as a lit block face; earlier this filled the
        // middle with random speckles, which at icon size just looked like static
        int arc = Math.max(2, size / 6);
        g.setPaint(new GradientPaint(
                0, 0, mix(base, Color.WHITE, 0.22f),
                size, size, mix(base, Color.BLACK, 0.22f)));
        g.fillRoundRect(0, 0, size, size, arc, arc);

        g.setColor(mix(base, Color.BLACK, 0.35f));
        g.setStroke(new BasicStroke(Math.max(1f, size / 32f)));
        g.drawRoundRect(1, 1, size - 2, size - 2, arc, arc);

        g.dispose();
        return image;
    }

    private static Color mix(Color from, Color to, float amount) {
        int r = Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount);
        int g = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount);
        int b = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount);
        return new Color(r, g, b);
    }
}
