package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.function.Supplier;

/**
 * The screenshots Minecraft itself dropped into this instance's {@code screenshots}
 * folder - browsed as a gallery instead of having to go dig through Explorer for them.
 */
@Slf4j
public class ScreenshotsPanel extends BackdropPanel {
    private static final int THUMB = 96;
    private static final String CARD_LIST = "list";
    private static final String CARD_EMPTY = "empty";

    private final Supplier<Instance> instanceSource;
    private final DefaultListModel<File> model = new DefaultListModel<>();
    private final JList<File> list = new JList<>(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);

    public ScreenshotsPanel(Supplier<Instance> instanceSource) {
        this.instanceSource = instanceSource;
        setVgap(SwingUtil.magnify(8));

        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(0);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((l, file, index, isSelected, cellHasFocus) -> {
            JPanel cell = new JPanel(new BorderLayout(0, SwingUtil.magnify(4)));
            cell.setBorder(BorderFactory.createEmptyBorder(
                    SwingUtil.magnify(6), SwingUtil.magnify(6), SwingUtil.magnify(6), SwingUtil.magnify(6)));
            cell.setOpaque(isSelected);
            if (isSelected) {
                cell.setBackground(l.getSelectionBackground());
            }

            JLabel thumb = new JLabel(thumbnail(file));
            thumb.setHorizontalAlignment(SwingConstants.CENTER);
            thumb.setPreferredSize(new java.awt.Dimension(SwingUtil.magnify(THUMB), SwingUtil.magnify(THUMB)));
            cell.add(thumb, BorderLayout.CENTER);

            JLabel name = new JLabel(file.getName());
            name.setHorizontalAlignment(SwingConstants.CENTER);
            name.setFont(name.getFont().deriveFont(name.getFont().getSize2D() - 2f));
            if (isSelected) {
                name.setForeground(l.getSelectionForeground());
            }
            cell.add(name, BorderLayout.SOUTH);

            return cell;
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);

        JLabel emptyHint = new JLabel(ModrinthStrings.get("instance.screenshots.empty"));
        emptyHint.setHorizontalAlignment(SwingConstants.CENTER);
        emptyHint.setEnabled(false);

        center.setOpaque(false);
        center.add(scroll, CARD_LIST);
        center.add(emptyHint, CARD_EMPTY);
        setCenter(center);

        JButton open = new JButton(ModrinthStrings.get("instance.screenshots.open"));
        open.setIcon(Images.getIcon16("external-link"));
        open.addActionListener(e -> openSelected());

        JButton delete = new JButton(ModrinthStrings.get("instances.delete"));
        delete.setIcon(Images.getIcon16("trash"));
        delete.addActionListener(e -> deleteSelected());

        JButton folder = new JButton(ModrinthStrings.get("instances.open-folder"));
        folder.setIcon(Images.getIcon16("folder-open"));
        folder.addActionListener(e -> {
            Instance instance = instanceSource.get();
            if (instance != null) {
                OS.openFolder(screenshotsDir(instance));
            }
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(4), 0));
        south.setOpaque(false);
        south.add(open);
        south.add(delete);
        south.add(folder);
        setSouth(south);
    }

    public void onShown() {
        Instance instance = instanceSource.get();
        model.clear();
        if (instance != null) {
            File dir = screenshotsDir(instance);
            File[] files = dir.listFiles((d, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".png"));
            if (files != null) {
                java.util.Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                for (File file : files) {
                    model.addElement(file);
                }
            }
        }
        showCurrentCard();
    }

    private void openSelected() {
        File file = list.getSelectedValue();
        if (file != null) {
            OS.openFile(file);
        }
    }

    private void deleteSelected() {
        File file = list.getSelectedValue();
        if (file == null) {
            return;
        }
        if (!Alert.showQuestion(ModrinthStrings.get("instances.delete"),
                ModrinthStrings.get("instance.screenshots.confirm-delete", file.getName()))) {
            return;
        }
        if (file.delete()) {
            model.removeElement(file);
            showCurrentCard();
        } else {
            Alert.showError(ModrinthStrings.get("error.title"), file.getName());
        }
    }

    private void showCurrentCard() {
        cards.show(center, model.isEmpty() ? CARD_EMPTY : CARD_LIST);
    }

    private static ImageIcon thumbnail(File file) {
        int size = SwingUtil.magnify(THUMB);
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return new ImageIcon();
            }
            int w = image.getWidth(), h = image.getHeight();
            double scale = Math.min((double) size / w, (double) size / h);
            int scaledW = Math.max(1, (int) (w * scale)), scaledH = Math.max(1, (int) (h * scale));
            return new ImageIcon(image.getScaledInstance(scaledW, scaledH, Image.SCALE_FAST));
        } catch (IOException e) {
            log.debug("Could not load thumbnail for {}: {}", file, e.toString());
            return new ImageIcon();
        }
    }

    private static File screenshotsDir(Instance instance) {
        return new File(instance.getGameDir(), "screenshots");
    }
}
