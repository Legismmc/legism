package net.legacylauncher.ui.update;

import net.legacylauncher.ui.loc.Localizable;
import net.legacylauncher.update.SelfUpdater;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Frame;

/**
 * A small modal dialog shown while a launcher update downloads.
 * <p>
 * Deliberately has no cancel button: the download runs to a temporary file and the only
 * thing cancelling would save is a little bandwidth, while a half-finished installer left
 * behind by a cancel that raced the rename is a much worse outcome to reason about.
 */
public class UpdateProgressDialog implements SelfUpdater.ProgressListener {

    private final JDialog dialog;
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JLabel label = new JLabel();

    public UpdateProgressDialog(Frame owner, String fileName) {
        dialog = new JDialog(owner, Localizable.get("update.download.title"), true);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        label.setText(Localizable.get("update.download.progress", fileName));
        bar.setIndeterminate(true);
        bar.setStringPainted(true);

        JPanel content = new JPanel(new BorderLayout(0, SwingUtil.magnify(10)));
        content.setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(16), SwingUtil.magnify(18),
                SwingUtil.magnify(16), SwingUtil.magnify(18)));
        content.add(label, BorderLayout.NORTH);
        content.add(bar, BorderLayout.CENTER);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setSize(SwingUtil.magnify(420), dialog.getHeight());
        dialog.setLocationRelativeTo(owner);
    }

    /**
     * Blocks until {@link #done()} closes it, so the caller starts the download first.
     */
    public void showDialog() {
        dialog.setVisible(true);
    }

    @Override
    public void onProgress(long downloadedBytes, long totalBytes) {
        SwingUtilities.invokeLater(() -> {
            if (totalBytes <= 0) {
                return; // no content length to work from; the spinner says enough
            }
            bar.setIndeterminate(false);
            int percent = (int) Math.min(100, downloadedBytes * 100 / totalBytes);
            bar.setValue(percent);
            bar.setString(percent + "%  ("
                    + megabytes(downloadedBytes) + " / " + megabytes(totalBytes) + " MB)");
        });
    }

    public void done() {
        dialog.setVisible(false);
        dialog.dispose();
    }

    private static long megabytes(long bytes) {
        return bytes / (1024L * 1024L);
    }
}
