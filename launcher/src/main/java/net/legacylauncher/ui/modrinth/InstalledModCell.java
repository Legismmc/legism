package net.legacylauncher.ui.modrinth;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.modrinth.InstalledMod;
import net.legacylauncher.modrinth.ModInstaller;
import net.legacylauncher.modrinth.ModrinthMatch;
import net.legacylauncher.ui.images.IconLoader;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.util.EHttpClient;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;
import net.legacylauncher.util.ua.LauncherUserAgent;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpHeaders;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;

/**
 * One jar in the mods directory, with the buttons to disable or delete it - and, when
 * Modrinth recognises the file, its real icon and an update button.
 */
@Slf4j
public class InstalledModCell extends JPanel {
    private static final int ICON_SIZE = 24;

    private final JLabel icon = new JLabel();

    public InstalledModCell(ModrinthPanel panel, InstalledMod mod, ModrinthMatch match) {
        setLayout(new BorderLayout(SwingUtil.magnify(10), 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(6), SwingUtil.magnify(4),
                SwingUtil.magnify(6), SwingUtil.magnify(4)));
        setAlignmentX(LEFT_ALIGNMENT);

        int size = SwingUtil.magnify(ICON_SIZE);
        icon.setPreferredSize(new Dimension(size, size));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        icon.setIcon(Images.getIcon("puzzle-piece", size));
        icon.setEnabled(mod.isEnabled());
        add(icon, BorderLayout.WEST);
        if (match != null && StringUtils.isNotEmpty(match.getIconUrl())) {
            loadIconAsync(match.getIconUrl(), size);
        }

        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);

        JLabel name = new JLabel(match != null ? match.getTitle() : mod.getDisplayName());
        name.setEnabled(mod.isEnabled());
        text.add(name, BorderLayout.NORTH);

        StringBuilder meta = new StringBuilder(ModInstaller.formatSize(mod.getSize()));
        if (!mod.isEnabled()) {
            meta.append("  ·  ").append(ModrinthStrings.get("disable"));
        }
        boolean hasUpdate = match != null && match.hasUpdate();
        if (hasUpdate) {
            meta.append("  ·  ").append(ModrinthStrings.get("update-available"));
        }
        JLabel metaLabel = new JLabel(meta.toString());
        metaLabel.setEnabled(false);
        if (hasUpdate) {
            metaLabel.setForeground(new java.awt.Color(0x2e8b57));
            metaLabel.setEnabled(true);
        }
        text.add(metaLabel, BorderLayout.SOUTH);

        add(text, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(4), 0));
        buttons.setOpaque(false);

        if (hasUpdate) {
            JButton update = new JButton(ModrinthStrings.get("update"));
            update.setIcon(Images.getIcon16("download"));
            update.addActionListener(e -> panel.updateInstalled(mod, match, this));
            buttons.add(update);
        }

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

    /**
     * Mirrors {@link ModrinthProjectCell#loadIconAsync}: Modrinth serves project icons as
     * PNG, JPEG, GIF, WebP or SVG, and {@link IconLoader} makes sense of whichever comes
     * back.
     */
    private void loadIconAsync(String url, int size) {
        AsyncThread.execute(() -> {
            final BufferedImage image;
            try {
                Content content = EHttpClient.toContent(
                        Request.get(url).addHeader(HttpHeaders.USER_AGENT, LauncherUserAgent.USER_AGENT)
                );
                if (content == null) {
                    return;
                }
                image = IconLoader.decode(content.asBytes(), url, size);
            } catch (Exception e) {
                log.debug("Could not load the icon at {}: {}", url, e.toString());
                return;
            }
            if (image == null) {
                return;
            }
            final Image scaled = image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            SwingUtil.later(() -> {
                icon.setIcon(new ImageIcon(scaled));
                icon.repaint();
            });
        });
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
