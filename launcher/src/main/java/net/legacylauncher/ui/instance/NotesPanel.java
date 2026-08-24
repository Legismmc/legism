package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.util.function.Supplier;

/**
 * A free-text scratchpad kept next to the instance, for whatever's worth remembering about
 * it - which mod caused the last crash, a server's whitelist code, that sort of thing.
 */
@Slf4j
public class NotesPanel extends BackdropPanel {
    private final Supplier<Instance> instanceSource;
    private final JTextArea textArea = new JTextArea();
    private final JLabel status = new JLabel();

    public NotesPanel(Supplier<Instance> instanceSource) {
        this.instanceSource = instanceSource;
        setVgap(SwingUtil.magnify(8));

        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        setCenter(scroll);

        JButton save = new JButton(ModrinthStrings.get("instance.notes.save"));
        save.setIcon(Images.getIcon16("save"));
        save.addActionListener(e -> save());

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(save, BorderLayout.WEST);
        status.setEnabled(false);
        south.add(status, BorderLayout.EAST);
        setSouth(south);
    }

    public void onShown() {
        Instance instance = instanceSource.get();
        status.setText("");
        if (instance == null) {
            textArea.setText("");
            return;
        }
        File file = notesFile(instance);
        if (file.isFile()) {
            try {
                textArea.setText(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.warn("Could not read {}", file, e);
                textArea.setText("");
            }
        } else {
            textArea.setText("");
        }
        textArea.setCaretPosition(0);
    }

    private void save() {
        Instance instance = instanceSource.get();
        if (instance == null) {
            return;
        }
        File file = notesFile(instance);
        try {
            if (textArea.getText().isEmpty()) {
                Files.deleteIfExists(file.toPath());
            } else {
                Files.write(file.toPath(), textArea.getText().getBytes(StandardCharsets.UTF_8));
            }
            status.setText(ModrinthStrings.get("instance.notes.saved"));
        } catch (IOException e) {
            log.warn("Could not write {}", file, e);
            Alert.showError(ModrinthStrings.get("error.title"), String.valueOf(e.getMessage()));
        }
    }

    private static File notesFile(Instance instance) {
        return new File(instance.getFolder(), "notes.txt");
    }
}
