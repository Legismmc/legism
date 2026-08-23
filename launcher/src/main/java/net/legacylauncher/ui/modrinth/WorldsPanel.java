package net.legacylauncher.ui.modrinth;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.modrinth.ModInstaller;
import net.legacylauncher.modrinth.ModTarget;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.swing.extended.BorderPanel;
import net.legacylauncher.util.FileUtil;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The worlds of one instance.
 * <p>
 * Modrinth has no world project type, so unlike the other tabs this one has nothing to
 * browse: it lists the saves already on disk and can take in a world someone shared as a
 * zip.
 */
@Slf4j
public class WorldsPanel extends BorderPanel {
    /**
     * A save folder is recognised by this file, the same way the game does it.
     */
    private static final String LEVEL_FILE = "level.dat";

    private final Supplier<ModTarget> targetSource;
    private final JPanel list = new JPanel();
    private final JLabel status = new JLabel();

    private ModTarget target;

    public WorldsPanel(Supplier<ModTarget> targetSource) {
        this.targetSource = targetSource;
        setVgap(SwingUtil.magnify(8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(6), 0));
        top.setOpaque(false);

        JButton importButton = new JButton(ModrinthStrings.get("worlds.import"));
        importButton.setIcon(Images.getIcon16("download"));
        importButton.addActionListener(e -> importWorld());
        top.add(importButton);

        JButton openFolder = new JButton(ModrinthStrings.get("open-folder"));
        openFolder.setIcon(Images.getIcon16("folder-open"));
        openFolder.addActionListener(e -> openSavesFolder());
        top.add(openFolder);

        JButton refresh = new JButton(ModrinthStrings.get("refresh"));
        refresh.setIcon(Images.getIcon16("refresh"));
        refresh.addActionListener(e -> refresh());
        top.add(refresh);

        setNorth(top);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(list, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(wrapper,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(SwingUtil.magnify(16));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        setCenter(scroll);

        status.setText(ModrinthStrings.get("worlds.not-on-modrinth"));
        setSouth(status);
    }

    public void onShown() {
        target = targetSource.get();
        refresh();
    }

    private File savesDir() {
        return target == null ? null : target.getSavesDir();
    }

    private void refresh() {
        list.removeAll();

        List<File> worlds = findWorlds();
        if (worlds.isEmpty()) {
            list.add(new JLabel(ModrinthStrings.get("worlds.empty")));
        } else {
            for (File world : worlds) {
                list.add(new WorldCell(world));
            }
        }

        list.revalidate();
        list.repaint();
    }

    private List<File> findWorlds() {
        File saves = savesDir();
        File[] folders = saves == null ? null : saves.listFiles();
        if (folders == null) {
            return new ArrayList<>();
        }
        List<File> worlds = new ArrayList<>();
        for (File folder : folders) {
            if (folder.isDirectory() && new File(folder, LEVEL_FILE).isFile()) {
                worlds.add(folder);
            }
        }
        worlds.sort(Comparator.comparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT)));
        return worlds;
    }

    private void openSavesFolder() {
        final File saves = savesDir();
        if (saves == null) {
            return;
        }
        AsyncThread.execute(() -> {
            saves.mkdirs();
            OS.openFolder(saves);
        });
    }

    /**
     * Unpacks a shared world archive into the instance's saves folder.
     */
    private void importWorld() {
        File saves = savesDir();
        if (saves == null) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(ModrinthStrings.get("worlds.import.title"));
        chooser.setFileFilter(new FileNameExtensionFilter("Minecraft world (*.zip)", "zip"));
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File archive = chooser.getSelectedFile();

        AsyncThread.execute(() -> {
            try {
                String name = extract(archive, saves);
                SwingUtil.later(() -> {
                    status.setText(ModrinthStrings.get("worlds.imported", name));
                    refresh();
                });
            } catch (IOException e) {
                log.warn("Could not import the world {}", archive, e);
                SwingUtil.later(() -> Alert.showError(ModrinthStrings.get("error.title"),
                        ModrinthStrings.get("worlds.error.import") + "\n" + e.getMessage()));
            }
        });
    }

    /**
     * Extracts the archive into {@code saves}. Archives come in two shapes: the world
     * folder at the root, or its contents at the root. Both are handled, and entries are
     * checked so a crafted archive cannot write outside the saves folder.
     *
     * @return the name of the world folder that was created
     */
    static String extract(File archive, File saves) throws IOException {
        Path savesPath = saves.getAbsoluteFile().toPath().normalize();
        Files.createDirectories(savesPath);

        try (ZipFile zip = new ZipFile(archive)) {
            String prefix = findRootPrefix(zip);
            String worldName = prefix.isEmpty()
                    ? stripExtension(archive.getName())
                    : prefix.substring(0, prefix.length() - 1);
            worldName = uniqueName(saves, sanitize(worldName));

            Path destination = savesPath.resolve(worldName).normalize();
            if (!destination.startsWith(savesPath)) {
                throw new IOException("refusing to write outside the saves folder");
            }
            Files.createDirectories(destination);

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix)) {
                    continue;
                }
                String relative = name.substring(prefix.length());
                if (relative.isEmpty()) {
                    continue;
                }
                Path out = destination.resolve(relative).normalize();
                if (!out.startsWith(destination)) {
                    throw new IOException("archive entry escapes the world folder: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            if (!new File(destination.toFile(), LEVEL_FILE).isFile()) {
                FileUtil.deleteDirectory(destination.toFile());
                throw new IOException("the archive contains no " + LEVEL_FILE
                        + ", so it is not a Minecraft world");
            }
            return worldName;
        }
    }

    /**
     * @return {@code ""} when the archive holds a world at its root, otherwise the single
     * top-level folder name with a trailing slash
     */
    private static String findRootPrefix(ZipFile zip) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        String candidate = null;
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.equals(LEVEL_FILE)) {
                return "";
            }
            int slash = name.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String top = name.substring(0, slash + 1);
            if (candidate == null) {
                candidate = top;
            } else if (!candidate.equals(top)) {
                return ""; // several top level entries: treat the archive as the world
            }
        }
        return candidate == null ? "" : candidate;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String sanitize(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "world" : cleaned;
    }

    private static String uniqueName(File saves, String name) {
        if (!new File(saves, name).exists()) {
            return name;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = name + " (" + i + ")";
            if (!new File(saves, candidate).exists()) {
                return candidate;
            }
        }
        return name + " (" + System.currentTimeMillis() + ")";
    }

    /**
     * One save folder, with the buttons that act on it.
     */
    private class WorldCell extends JPanel {
        WorldCell(File world) {
            setLayout(new BorderLayout(SwingUtil.magnify(10), 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(
                    SwingUtil.magnify(6), SwingUtil.magnify(4),
                    SwingUtil.magnify(6), SwingUtil.magnify(4)));
            setAlignmentX(LEFT_ALIGNMENT);

            add(new JLabel(Images.getIcon24("cube")), BorderLayout.WEST);

            JPanel text = new JPanel(new BorderLayout());
            text.setOpaque(false);
            text.add(new JLabel(world.getName()), BorderLayout.NORTH);
            JLabel size = new JLabel(ModrinthStrings.get("worlds.size",
                    ModInstaller.formatSize(directorySize(world))));
            size.setEnabled(false);
            text.add(size, BorderLayout.SOUTH);
            add(text, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, SwingUtil.magnify(4), 0));
            buttons.setOpaque(false);

            JButton open = new JButton();
            open.setIcon(Images.getIcon16("folder-open"));
            open.setToolTipText(ModrinthStrings.get("open-folder"));
            open.addActionListener(e -> AsyncThread.execute(() -> OS.openFolder(world)));
            buttons.add(open);

            JButton delete = new JButton();
            delete.setIcon(Images.getIcon16("trash"));
            delete.setToolTipText(ModrinthStrings.get("delete"));
            delete.addActionListener(e -> deleteWorld(world));
            buttons.add(delete);

            add(buttons, BorderLayout.EAST);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private void deleteWorld(File world) {
        if (!Alert.showQuestion(ModrinthStrings.get("error.title"),
                ModrinthStrings.get("confirm.delete", world.getName()))) {
            return;
        }
        FileUtil.deleteDirectory(world);
        if (world.exists()) {
            Alert.showError(ModrinthStrings.get("error.title"),
                    ModrinthStrings.get("error.delete"));
        }
        refresh();
    }

    private static long directorySize(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0L;
        }
        long total = 0L;
        for (File file : files) {
            total += file.isDirectory() ? directorySize(file) : file.length();
        }
        return total;
    }
}
