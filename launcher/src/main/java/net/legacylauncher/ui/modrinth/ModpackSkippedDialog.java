package net.legacylauncher.ui.modrinth;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;
import org.apache.commons.lang3.StringUtils;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lists the files of a freshly installed modpack that CurseForge would not hand over,
 * because their authors only allow downloads through CurseForge's own app.
 * <p>
 * The launcher deliberately does not work around that. What it does do is make the manual
 * part as short as possible: each file links straight to its download page, and while
 * this window is open anything landing in the Downloads folder under the right name - and
 * with the right hash, when CurseForge published one - is moved into the instance by
 * itself. Click, download, done.
 */
@Slf4j
public class ModpackSkippedDialog {
    private static final int SCAN_INTERVAL_MS = 2000;

    private final JDialog dialog;
    private final List<ModpackImporter.SkippedFile> files;
    private final List<JLabel> statusLabels = new ArrayList<>();
    private final JLabel summary = new JLabel();
    private final AtomicBoolean scanning = new AtomicBoolean();
    private final Timer timer;

    public static void showIfNeeded(Component parent, ModpackImporter.Result result) {
        if (result.getSkipped().isEmpty()) {
            return;
        }
        new ModpackSkippedDialog(parent, result).dialog.setVisible(true);
    }

    private ModpackSkippedDialog(Component parent, ModpackImporter.Result result) {
        this.files = result.getSkipped();
        Window owner = parent == null || parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        dialog = new JDialog(owner, ModrinthStrings.get("modpack.skipped.title"), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        // an HTML label only wraps once it is told how wide it may be - and Swing scales
        // CSS pixels up, so this is narrower than the window on purpose
        JLabel intro = new JLabel("<html><body style='width:" + SwingUtil.magnify(430) + "px'>"
                + ModrinthStrings.get("modpack.skipped.intro",
                result.getInstance().getName(), files.size()) + "</html>");

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (ModpackImporter.SkippedFile file : files) {
            list.add(row(file));
            list.add(Box.createVerticalStrut(SwingUtil.magnify(6)));
        }
        JPanel listHolder = new JPanel(new BorderLayout());
        listHolder.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(listHolder);
        scroll.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") != null
                ? UIManager.getColor("Component.borderColor") : java.awt.Color.GRAY));
        scroll.getVerticalScrollBar().setUnitIncrement(SwingUtil.magnify(16));

        JButton openFolder = new JButton(ModrinthStrings.get("modpack.skipped.open-folder"));
        openFolder.addActionListener(e -> {
            File folder = files.get(0).getFolder();
            folder.mkdirs();
            OS.openFolder(folder);
        });
        JButton close = new JButton(ModrinthStrings.get("modpack.skipped.close"));
        close.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(8), 0));
        buttons.add(openFolder);
        buttons.add(close);

        JPanel south = new JPanel(new BorderLayout(SwingUtil.magnify(10), 0));
        south.add(summary, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, SwingUtil.magnify(12)));
        content.setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(16), SwingUtil.magnify(18),
                SwingUtil.magnify(14), SwingUtil.magnify(18)));
        content.add(intro, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(content);

        dialog.setSize(SwingUtil.magnify(new Dimension(640, Math.min(600, 270 + files.size() * 50))));
        dialog.setMinimumSize(SwingUtil.magnify(new Dimension(480, 260)));
        dialog.setLocationRelativeTo(owner);

        timer = new Timer(SCAN_INTERVAL_MS, e -> scan());
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                timer.start();
            }

            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                timer.stop();
            }
        });
        refreshStatuses();
    }

    private JPanel row(ModpackImporter.SkippedFile file) {
        JLabel name = new JLabel(file.getName());
        name.setFont(name.getFont().deriveFont(java.awt.Font.BOLD));
        JLabel fileName = new JLabel(StringUtils.defaultString(file.getFileName()));
        fileName.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(name);
        text.add(fileName);

        JLabel status = new JLabel();
        statusLabels.add(status);

        JButton open = new JButton(ModrinthStrings.get("modpack.skipped.download"));
        open.setEnabled(file.getPageUrl() != null);
        open.addActionListener(e -> OS.openLink(file.getPageUrl()));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(8), 0));
        actions.setOpaque(false);
        actions.add(status);
        actions.add(open);

        JPanel row = new JPanel(new BorderLayout(SwingUtil.magnify(10), 0));
        row.setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(6), SwingUtil.magnify(10), SwingUtil.magnify(6), SwingUtil.magnify(6)));
        row.add(text, BorderLayout.CENTER);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private void refreshStatuses() {
        int done = 0;
        for (int i = 0; i < files.size(); i++) {
            boolean inPlace = files.get(i).isInPlace();
            if (inPlace) {
                done++;
            }
            JLabel status = statusLabels.get(i);
            status.setText(ModrinthStrings.get(inPlace ? "modpack.skipped.in-place" : "modpack.skipped.waiting"));
            status.setForeground(inPlace ? new java.awt.Color(0x3FA34D) : UIManager.getColor("Label.disabledForeground"));
        }
        summary.setText(ModrinthStrings.get("modpack.skipped.progress", done, files.size()));
    }

    /**
     * Picks up any of the missing files that have appeared in a Downloads folder. Off the
     * Swing thread, since checking a hash means reading the whole file.
     */
    private void scan() {
        if (!scanning.compareAndSet(false, true)) {
            return;
        }
        AsyncThread.execute(() -> {
            try {
                for (ModpackImporter.SkippedFile file : files) {
                    if (file.getFileName() == null || file.isInPlace()) {
                        continue;
                    }
                    for (File downloads : downloadFolders()) {
                        File candidate = new File(downloads, file.getFileName());
                        if (candidate.isFile() && adopt(file, candidate)) {
                            break;
                        }
                    }
                }
            } finally {
                scanning.set(false);
                SwingUtilities.invokeLater(this::refreshStatuses);
            }
        });
    }

    private static boolean adopt(ModpackImporter.SkippedFile file, File candidate) {
        try {
            if (StringUtils.isNotEmpty(file.getSha1())
                    && !file.getSha1().equalsIgnoreCase(ModpackImporter.sha1(candidate))) {
                // probably still downloading; a finished but different file stays where it is
                return false;
            }
            File target = ModpackImporter.target(file.getFolder(), file.getFileName());
            Files.createDirectories(target.getParentFile().toPath());
            Files.move(candidate.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("Moved the hand-downloaded {} into {}", candidate, target);
            return true;
        } catch (IOException e) {
            // the browser may still hold the file open - the next scan will try again
            log.debug("Could not take {} yet: {}", candidate, e.toString());
            return false;
        }
    }

    private static List<File> downloadFolders() {
        List<File> folders = new ArrayList<>();
        if (OS.WINDOWS.isCurrent()) {
            // Windows lets the Downloads folder be moved anywhere, D:\ included
            try {
                File known = new File(com.sun.jna.platform.win32.Shell32Util.getKnownFolderPath(
                        com.sun.jna.platform.win32.KnownFolders.FOLDERID_Downloads));
                if (known.isDirectory()) {
                    folders.add(known);
                }
            } catch (Throwable t) {
                log.debug("Could not ask Windows where Downloads is: {}", t.toString());
            }
        }
        File home = new File(System.getProperty("user.home"));
        for (String name : new String[]{"Downloads", "Загрузки"}) {
            File folder = new File(home, name);
            if (folder.isDirectory() && !folders.contains(folder)) {
                folders.add(folder);
            }
        }
        return folders;
    }
}
