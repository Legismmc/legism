package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.util.function.Supplier;

/**
 * The tail end of this instance's own {@code logs/latest.log} - what Minecraft printed
 * last time it ran, without having to open the folder and a text editor for it.
 */
@Slf4j
public class LogPanel extends BackdropPanel {
    private static final long MAX_BYTES = 512 * 1024;

    private final Supplier<Instance> instanceSource;
    private final JTextArea textArea = new JTextArea();

    public LogPanel(Supplier<Instance> instanceSource) {
        this.instanceSource = instanceSource;
        setVgap(SwingUtil.magnify(8));

        textArea.setEditable(false);
        textArea.setLineWrap(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, textArea.getFont().getSize()));
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        setCenter(scroll);

        JButton refresh = new JButton(ModrinthStrings.get("refresh"));
        refresh.setIcon(Images.getIcon16("refresh"));
        refresh.addActionListener(e -> onShown());

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(refresh, BorderLayout.WEST);
        setSouth(south);
    }

    public void onShown() {
        Instance instance = instanceSource.get();
        if (instance == null) {
            textArea.setText("");
            return;
        }
        File file = new File(instance.getGameDir(), "logs/latest.log");
        if (!file.isFile()) {
            textArea.setText(ModrinthStrings.get("instance.log.none"));
            return;
        }
        try {
            long length = file.length();
            byte[] bytes;
            if (length > MAX_BYTES) {
                bytes = new byte[(int) MAX_BYTES];
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
                    raf.seek(length - MAX_BYTES);
                    raf.readFully(bytes);
                }
            } else {
                bytes = Files.readAllBytes(file.toPath());
            }
            textArea.setText((length > MAX_BYTES ? "[...]\n" : "") + new String(bytes, StandardCharsets.UTF_8));
            textArea.setCaretPosition(textArea.getDocument().getLength());
        } catch (IOException e) {
            log.warn("Could not read {}", file, e);
            textArea.setText(ModrinthStrings.get("instance.log.none"));
        }
    }
}
