package net.legacylauncher.ui.modrinth;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.modrinth.ModInstaller;
import net.legacylauncher.modrinth.ModrinthProject;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.util.EHttpClient;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;
import net.legacylauncher.util.ua.LauncherUserAgent;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpHeaders;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.awt.FlowLayout;
import java.util.Locale;

/**
 * One search hit: icon, title, description and the buttons that act on it.
 */
@Slf4j
public class ModrinthProjectCell extends JPanel {
    private static final int ICON_SIZE = 48;
    private static final int DESCRIPTION_WIDTH = 420;

    private final ModrinthPanel panel;
    private final ModrinthProject project;

    private final JButton installButton = new JButton();
    private final JLabel iconLabel = new JLabel();

    public ModrinthProjectCell(ModrinthPanel panel, ModrinthProject project) {
        this.panel = panel;
        this.project = project;

        setLayout(new BorderLayout(SwingUtil.magnify(10), 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(8), SwingUtil.magnify(4),
                SwingUtil.magnify(8), SwingUtil.magnify(4)));
        setAlignmentX(LEFT_ALIGNMENT);

        add(buildIcon(), BorderLayout.WEST);
        add(buildText(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.EAST);
    }

    private JLabel buildIcon() {
        int size = SwingUtil.magnify(ICON_SIZE);
        iconLabel.setPreferredSize(new Dimension(size, size));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setIcon(Images.getIcon("puzzle-piece", size));
        loadIconAsync(size);
        return iconLabel;
    }

    private JPanel buildText() {
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BorderLayout(0, SwingUtil.magnify(2)));

        JLabel title = new JLabel(project.getTitle());
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        text.add(title, BorderLayout.NORTH);

        JLabel description = new JLabel(html(project.getDescription(), DESCRIPTION_WIDTH));
        text.add(description, BorderLayout.CENTER);

        StringBuilder meta = new StringBuilder();
        if (StringUtils.isNotEmpty(project.getAuthor())) {
            meta.append(project.getAuthor());
        }
        meta.append(meta.length() > 0 ? "  ·  " : "")
                .append(ModInstaller.formatCount(project.getDownloads()))
                .append(" ↓");
        if (!project.getDisplayCategories().isEmpty()) {
            meta.append("  ·  ")
                    .append(StringUtils.join(project.getDisplayCategories(), ", "));
        }
        JLabel metaLabel = new JLabel(meta.toString());
        metaLabel.setEnabled(false);
        text.add(metaLabel, BorderLayout.SOUTH);

        return text;
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(4), 0));
        buttons.setOpaque(false);

        installButton.setText(ModrinthStrings.get("install"));
        installButton.setIcon(Images.getIcon16("download"));
        installButton.addActionListener(e -> panel.install(project, this));
        buttons.add(installButton);

        JButton open = new JButton();
        open.setIcon(Images.getIcon16("external-link"));
        open.setToolTipText(ModrinthStrings.get("open"));
        open.addActionListener(e -> OS.openLink(project.getPageUrl()));
        buttons.add(open);

        return buttons;
    }

    void setBusy(String text) {
        installButton.setEnabled(false);
        installButton.setText(text);
    }

    void setIdle() {
        installButton.setEnabled(true);
        installButton.setText(ModrinthStrings.get("install"));
    }

    void setInstalled() {
        installButton.setEnabled(false);
        installButton.setText(ModrinthStrings.get("installed"));
    }

    /**
     * Modrinth serves project icons as PNG, WebP or SVG. Only the raster formats Java can
     * decode are fetched; anything else keeps the generic puzzle-piece placeholder.
     */
    private void loadIconAsync(int size) {
        final String url = project.getIconUrl();
        if (StringUtils.isEmpty(url) || url.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            return;
        }
        AsyncThread.execute(() -> {
            final BufferedImage image;
            try {
                Content content = EHttpClient.toContent(
                        Request.get(url).addHeader(HttpHeaders.USER_AGENT, LauncherUserAgent.USER_AGENT)
                );
                if (content == null) {
                    return;
                }
                image = ImageIO.read(new ByteArrayInputStream(content.asBytes()));
            } catch (Exception e) {
                log.debug("Could not load the icon of {}: {}", project, e.toString());
                return;
            }
            if (image == null) {
                return;
            }
            final Image scaled = image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            SwingUtil.later(() -> {
                iconLabel.setIcon(new javax.swing.ImageIcon(scaled));
                iconLabel.repaint();
            });
        });
    }

    /**
     * Wraps plain text into a fixed width HTML label. The text comes from Modrinth, so
     * anything that could be read as markup is escaped first.
     */
    static String html(String text, int width) {
        return "<html><body style='width:" + SwingUtil.magnify(width) + "px'>"
                + escape(text) + "</body></html>";
    }

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    result.append("&amp;");
                    break;
                case '<':
                    result.append("&lt;");
                    break;
                case '>':
                    result.append("&gt;");
                    break;
                case '"':
                    result.append("&quot;");
                    break;
                case '\'':
                    result.append("&#39;");
                    break;
                default:
                    result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * A cell must not stretch vertically when the list has spare room.
     */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
