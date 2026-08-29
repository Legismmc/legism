package net.legacylauncher.ui.modrinth;

import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * A row of page numbers under a list of search results.
 * <p>
 * Shared by every screen that pages through a content library, so the awkward part - which
 * numbers to show when there are hundreds of pages - is written once.
 */
public class PagerBar extends JPanel {
    /**
     * Told which page the user picked, counting from zero.
     */
    public interface Listener {
        void onPageSelected(int page);
    }

    /**
     * How many pages to show either side of the current one. With the first and last
     * always present that makes at most nine buttons, which fits without wrapping.
     */
    private static final int WINDOW = 2;

    /**
     * Stands in for a run of pages too far from the current one to list.
     */
    private static final int GAP = -1;

    private final Listener listener;

    public PagerBar(Listener listener) {
        super(new FlowLayout(FlowLayout.CENTER, SwingUtil.magnify(4), SwingUtil.magnify(6)));
        this.listener = listener;
        setOpaque(false);
        setVisible(false);
    }

    /**
     * How many pages there are to offer: as many as the results fill, but never more than
     * the library will actually answer for.
     *
     * @param maxSearchDepth the largest {@code offset + pageSize} the provider allows,
     *                       from {@link net.legacylauncher.modrinth.ContentProvider#getMaxSearchDepth()}
     */
    public static int pageCount(int totalResults, int pageSize, int maxSearchDepth) {
        if (pageSize <= 0 || totalResults <= 0) {
            return 0;
        }
        int byResults = (totalResults + pageSize - 1) / pageSize;
        int lastReachableOffset = maxSearchDepth - pageSize;
        if (lastReachableOffset < 0) {
            return Math.min(byResults, 1);
        }
        int byDepth = lastReachableOffset / pageSize + 1;
        return Math.min(byResults, byDepth);
    }

    /**
     * Rebuilds the row. A single page needs no navigation at all, so the bar hides itself.
     *
     * @param currentPage counting from zero
     */
    public void update(int currentPage, int totalPages) {
        removeAll();
        if (totalPages <= 1) {
            setVisible(false);
            revalidate();
            repaint();
            return;
        }
        setVisible(true);

        addArrow("‹", currentPage - 1, currentPage > 0);
        for (int page : pagesToShow(currentPage, totalPages)) {
            if (page == GAP) {
                add(gapLabel());
            } else {
                addPage(page, page == currentPage);
            }
        }
        addArrow("›", currentPage + 1, currentPage < totalPages - 1);

        revalidate();
        repaint();
    }

    /**
     * The first page, the last page, and everything within {@link #WINDOW} of the current
     * one, with a gap marker standing in for each stretch left out.
     */
    private List<Integer> pagesToShow(int currentPage, int totalPages) {
        List<Integer> pages = new ArrayList<>();
        for (int page = 0; page < totalPages; page++) {
            boolean edge = page == 0 || page == totalPages - 1;
            boolean nearby = Math.abs(page - currentPage) <= WINDOW;
            if (edge || nearby) {
                pages.add(page);
            } else if (!pages.isEmpty() && pages.get(pages.size() - 1) != GAP) {
                pages.add(GAP);
            }
        }
        return pages;
    }

    private void addPage(int page, boolean current) {
        JButton button = new JButton(String.valueOf(page + 1));
        button.setMargin(new java.awt.Insets(2, SwingUtil.magnify(7), 2, SwingUtil.magnify(7)));
        button.setFocusable(!current);
        // the page you are already on stays visible but inert, so the row does not jump
        // around as the highlight moves
        button.setEnabled(!current);
        if (current) {
            button.setFont(button.getFont().deriveFont(java.awt.Font.BOLD));
        }
        button.addActionListener(e -> listener.onPageSelected(page));
        add(button);
    }

    private void addArrow(String glyph, int page, boolean enabled) {
        JButton button = new JButton(glyph);
        button.setMargin(new java.awt.Insets(2, SwingUtil.magnify(7), 2, SwingUtil.magnify(7)));
        button.setEnabled(enabled);
        button.addActionListener(e -> listener.onPageSelected(page));
        add(button);
    }

    private JLabel gapLabel() {
        JLabel label = new JLabel("…");
        label.setBorder(BorderFactory.createEmptyBorder(0, SwingUtil.magnify(4), 0, SwingUtil.magnify(4)));
        return label;
    }
}
