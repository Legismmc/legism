package net.legacylauncher.ui.instance;

import net.legacylauncher.instance.Instance;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
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
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Everything under {@code logs/} besides {@code latest.log} (rotated, gzipped old runs)
 * plus whatever's sitting in {@code crash-reports/} - one place to find an old log without
 * digging through the instance folder by hand.
 */
public class OtherLogsPanel extends BackdropPanel {
    private static final String CARD_LIST = "list";
    private static final String CARD_EMPTY = "empty";

    private final Supplier<Instance> instanceSource;
    private final DefaultListModel<File> model = new DefaultListModel<>();
    private final JList<File> list = new JList<>(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);

    public OtherLogsPanel(Supplier<Instance> instanceSource) {
        this.instanceSource = instanceSource;
        setVgap(SwingUtil.magnify(8));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((l, file, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(displayName(file));
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(
                    SwingUtil.magnify(4), SwingUtil.magnify(8), SwingUtil.magnify(4), SwingUtil.magnify(8)));
            label.setBackground(isSelected ? l.getSelectionBackground() : l.getBackground());
            label.setForeground(isSelected ? l.getSelectionForeground() : l.getForeground());
            return label;
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);

        JLabel emptyHint = new JLabel(ModrinthStrings.get("instance.other-logs.empty"));
        emptyHint.setHorizontalAlignment(SwingConstants.CENTER);
        emptyHint.setEnabled(false);

        center.setOpaque(false);
        center.add(scroll, CARD_LIST);
        center.add(emptyHint, CARD_EMPTY);
        setCenter(center);

        JButton open = new JButton(ModrinthStrings.get("instance.screenshots.open"));
        open.setIcon(Images.getIcon16("external-link"));
        open.addActionListener(e -> {
            File file = list.getSelectedValue();
            if (file != null) {
                OS.openFile(file);
            }
        });

        JButton refresh = new JButton(ModrinthStrings.get("refresh"));
        refresh.setIcon(Images.getIcon16("refresh"));
        refresh.addActionListener(e -> onShown());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(4), 0));
        south.setOpaque(false);
        south.add(open);
        south.add(refresh);
        setSouth(south);
    }

    public void onShown() {
        Instance instance = instanceSource.get();
        model.clear();
        if (instance != null) {
            List<File> files = new ArrayList<>();
            File logsDir = new File(instance.getGameDir(), "logs");
            File[] logFiles = logsDir.listFiles((d, name) -> !"latest.log".equals(name));
            if (logFiles != null) {
                for (File file : logFiles) {
                    files.add(file);
                }
            }
            File crashDir = new File(instance.getGameDir(), "crash-reports");
            File[] crashFiles = crashDir.listFiles((d, name) -> name.endsWith(".txt"));
            if (crashFiles != null) {
                for (File file : crashFiles) {
                    files.add(file);
                }
            }
            files.sort(Comparator.comparingLong(File::lastModified).reversed());
            for (File file : files) {
                model.addElement(file);
            }
        }
        showCurrentCard();
    }

    private void showCurrentCard() {
        cards.show(center, model.isEmpty() ? CARD_EMPTY : CARD_LIST);
    }

    private static String displayName(File file) {
        String parent = file.getParentFile() == null ? "" : file.getParentFile().getName();
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new java.util.Date(file.lastModified()));
        return parent + "/" + file.getName() + "  —  " + when;
    }
}
