package net.legacylauncher.ui.modrinth;

import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shows a modpack install as it happens: which phase it is in, how many of the pack's
 * files are done, and how far along the one downloading right now is.
 * <p>
 * Before this the only sign of life was a short label on the catalog card, which sat on
 * "Downloading..." for the whole of a large pack and read as frozen - and a pack that did
 * fail gave no clue how far it had got.
 * <p>
 * Usage: create it on the Swing thread, start the install on a worker with this as its
 * listener, then call {@link #showDialog()}, which blocks until the worker calls
 * {@link #done()}.
 */
public class ModpackInstallDialog implements ModpackImporter.ProgressListener {
    private final JDialog dialog;
    private final JLabel stageLabel = new JLabel(" ");
    private final JProgressBar overallBar = new JProgressBar();
    private final JLabel fileLabel = new JLabel(" ");
    private final JProgressBar fileBar = new JProgressBar(0, 1000);
    private final JButton cancelButton = new JButton(ModrinthStrings.get("modpack.progress.cancel"));

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();

    // the latest byte counts, picked up by at most one pending repaint at a time - a big
    // pack reports thousands of chunks and the screen only needs the newest one
    private volatile long bytesDone;
    private volatile long bytesTotal;
    private final AtomicBoolean bytesPending = new AtomicBoolean();

    public ModpackInstallDialog(Component parent, String packName) {
        Window owner = parent == null || parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        dialog = new JDialog(owner, ModrinthStrings.get("modpack.progress.title"), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel();
            }
        });

        JLabel nameLabel = new JLabel(packName);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, nameLabel.getFont().getSize2D() * 1.25f));

        overallBar.setIndeterminate(true);
        overallBar.setStringPainted(true);
        overallBar.setString("");
        fileBar.setStringPainted(true);
        fileBar.setString("");
        fileBar.setVisible(false);

        cancelButton.addActionListener(e -> cancel());

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.add(left(nameLabel));
        rows.add(Box.createVerticalStrut(SwingUtil.magnify(12)));
        rows.add(left(stageLabel));
        rows.add(Box.createVerticalStrut(SwingUtil.magnify(6)));
        rows.add(left(overallBar));
        rows.add(Box.createVerticalStrut(SwingUtil.magnify(12)));
        rows.add(left(fileLabel));
        rows.add(Box.createVerticalStrut(SwingUtil.magnify(6)));
        rows.add(left(fileBar));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(cancelButton);

        JPanel content = new JPanel(new BorderLayout(0, SwingUtil.magnify(14)));
        content.setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(18), SwingUtil.magnify(20),
                SwingUtil.magnify(16), SwingUtil.magnify(20)));
        content.add(rows, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setSize(SwingUtil.magnify(480), dialog.getHeight() + SwingUtil.magnify(24));
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
    }

    private static JComponent left(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (component instanceof JProgressBar) {
            component.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        }
        return component;
    }

    /**
     * Blocks until {@link #done()} closes it.
     */
    public void showDialog() {
        if (!finished.get()) {
            dialog.setVisible(true);
        }
    }

    /**
     * Closes the dialog. Safe from any thread, and safe to call before
     * {@link #showDialog()} has had a chance to open it.
     */
    public void done() {
        finished.set(true);
        SwingUtilities.invokeLater(() -> {
            dialog.setVisible(false);
            dialog.dispose();
        });
    }

    private void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancelButton.setEnabled(false);
            stageLabel.setText(ModrinthStrings.get("modpack.progress.cancelling"));
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void onStage(ModpackImporter.Stage stage) {
        SwingUtilities.invokeLater(() -> {
            if (cancelled.get()) {
                return;
            }
            stageLabel.setText(ModrinthStrings.get("modpack.progress.stage." + stage.name().toLowerCase(Locale.ROOT)));
            switch (stage) {
                case DOWNLOADING_PACK:
                    overallBar.setIndeterminate(true);
                    overallBar.setString("");
                    fileLabel.setText(" ");
                    fileBar.setVisible(true);
                    resetFileBar();
                    break;
                case DOWNLOADING_FILES:
                    // several files download at once, so the lower bar is the whole
                    // pack's megabytes rather than any one file's
                    overallBar.setIndeterminate(false);
                    fileLabel.setText(" ");
                    fileBar.setVisible(true);
                    resetFileBar();
                    break;
                case RESOLVING:
                case EXTRACTING:
                default:
                    overallBar.setIndeterminate(true);
                    overallBar.setString("");
                    fileLabel.setText(" ");
                    fileBar.setVisible(false);
                    break;
            }
        });
    }

    @Override
    public void onStep(String message, int current, int total) {
        SwingUtilities.invokeLater(() -> {
            overallBar.setIndeterminate(false);
            overallBar.setMaximum(Math.max(1, total));
            // current counts finished files
            overallBar.setValue(current);
            overallBar.setString(ModrinthStrings.get("modpack.progress.files", current, total));
            if (message != null && !message.isEmpty()) {
                fileLabel.setText(message);
            }
        });
    }

    @Override
    public void onBytes(long done, long total) {
        bytesDone = done;
        bytesTotal = total;
        if (!bytesPending.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            bytesPending.set(false);
            long d = bytesDone;
            long t = bytesTotal;
            if (t > 0) {
                fileBar.setIndeterminate(false);
                fileBar.setValue((int) Math.min(1000, d * 1000 / t));
                fileBar.setString(megabytes(d) + " / " + megabytes(t) + " MB");
            } else {
                fileBar.setIndeterminate(true);
                fileBar.setString(megabytes(d) + " MB");
            }
        });
    }

    private void resetFileBar() {
        fileBar.setIndeterminate(false);
        fileBar.setValue(0);
        fileBar.setString("");
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
    }
}
