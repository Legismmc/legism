package net.legacylauncher.ui.server;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.server.ServerInstance;
import net.legacylauncher.server.ServerInstanceManager;
import net.legacylauncher.ui.MainPane;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;

/**
 * List of the user's own local servers on the left, the selected one's console/properties/
 * plugins on the right - the hosting equivalent of {@code InstancesPanel}, minus the grid
 * of cards since a handful of hosted servers does not need one.
 */
@Slf4j
public class ServerHostingPanel extends JPanel {
    private final MainPane pane;
    private final ServerInstanceManager manager = LegacyLauncher.getInstance().getServerInstanceManager();

    private final DefaultListModel<ServerInstance> listModel = new DefaultListModel<>();
    private final JList<ServerInstance> list = new JList<>(listModel);
    private final JPanel detailContainer = new JPanel(new BorderLayout());
    private final JButton deleteButton = new JButton(ModrinthStrings.get("server.delete"));

    public ServerHostingPanel(MainPane pane) {
        super(new BorderLayout());
        this.pane = pane;

        JPanel left = new JPanel(new BorderLayout());
        left.setPreferredSize(SwingUtil.magnify(new Dimension(220, 0)));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(4), SwingUtil.magnify(4)));
        JButton create = new JButton(ModrinthStrings.get("server.create"));
        create.addActionListener(e -> createServer());
        toolbar.add(create);
        deleteButton.addActionListener(e -> deleteSelected());
        deleteButton.setEnabled(false);
        toolbar.add(deleteButton);
        left.add(toolbar, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(this::onSelectionChanged);
        left.add(new JScrollPane(list), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, detailContainer);
        split.setDividerLocation(SwingUtil.magnify(220));
        add(split, BorderLayout.CENTER);

        showEmptyHint(ModrinthStrings.get("server.empty"));
        refresh();
    }

    private void refresh() {
        ServerInstance selected = list.getSelectedValue();
        listModel.clear();
        for (ServerInstance server : manager.refresh()) {
            listModel.addElement(server);
        }
        if (selected != null) {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).getId().equals(selected.getId())) {
                    list.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void onSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        ServerInstance selected = list.getSelectedValue();
        deleteButton.setEnabled(selected != null);
        detailContainer.removeAll();
        if (selected == null) {
            showEmptyHint(ModrinthStrings.get("server.empty"));
        } else {
            detailContainer.add(new ServerDetailPanel(pane, manager, selected), BorderLayout.CENTER);
        }
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    private void showEmptyHint(String text) {
        JLabel label = new JLabel(text);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        detailContainer.removeAll();
        detailContainer.add(label, BorderLayout.CENTER);
    }

    private void createServer() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        CreateServerDialog dialog = new CreateServerDialog(owner);
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) {
            return;
        }
        try {
            ServerInstance created = manager.create(dialog.getServerName(), dialog.getCore(), dialog.getVersion());
            created.setXmx(dialog.getXmx());
            manager.save(created);
            refresh();
            selectById(created.getId());
        } catch (IOException e) {
            log.warn("Could not create a local server", e);
            Alert.showError(ModrinthStrings.get("server.error.create"), e.getMessage());
        }
    }

    private void selectById(String id) {
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).getId().equals(id)) {
                list.setSelectedIndex(i);
                break;
            }
        }
    }

    private void deleteSelected() {
        ServerInstance selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        if (!Alert.showQuestion(ModrinthStrings.get("server.delete"),
                ModrinthStrings.get("server.delete.confirm", selected.getName()))) {
            return;
        }
        try {
            manager.delete(selected);
            refresh();
        } catch (IOException e) {
            log.warn("Could not delete local server {}", selected, e);
            Alert.showError(ModrinthStrings.get("server.error.delete"), e.getMessage());
        }
    }
}
