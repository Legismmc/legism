package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.minecraft.NBTServer;
import net.legacylauncher.minecraft.Server;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.SwingUtil;
import org.apache.commons.lang3.StringUtils;

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
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.function.Supplier;

/**
 * The instance's own {@code servers.dat} - the multiplayer list Minecraft itself reads,
 * edited straight from the launcher instead of only from inside the game.
 */
@Slf4j
public class ServersPanel extends BackdropPanel {
    private static final String CARD_LIST = "list";
    private static final String CARD_EMPTY = "empty";

    private final Supplier<Instance> instanceSource;
    private final DefaultListModel<NBTServer> model = new DefaultListModel<>();
    private final JList<NBTServer> list = new JList<>(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);

    public ServersPanel(Supplier<Instance> instanceSource) {
        this.instanceSource = instanceSource;
        setVgap(SwingUtil.magnify(8));

        list.setCellRenderer((l, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.getName() + "  —  " + value.getFullAddress());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(
                    SwingUtil.magnify(4), SwingUtil.magnify(8), SwingUtil.magnify(4), SwingUtil.magnify(8)));
            label.setBackground(isSelected ? l.getSelectionBackground() : l.getBackground());
            label.setForeground(isSelected ? l.getSelectionForeground() : l.getForeground());
            return label;
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);

        JLabel emptyHint = new JLabel(ModrinthStrings.get("instance.servers.empty"));
        emptyHint.setHorizontalAlignment(SwingConstants.CENTER);
        emptyHint.setEnabled(false);

        center.setOpaque(false);
        center.add(scroll, CARD_LIST);
        center.add(emptyHint, CARD_EMPTY);
        setCenter(center);

        JButton add = new JButton(ModrinthStrings.get("instance.servers.add"));
        add.setIcon(Images.getIcon16("plus"));
        add.addActionListener(e -> addServer());

        JButton remove = new JButton(ModrinthStrings.get("instance.servers.remove"));
        remove.setIcon(Images.getIcon16("trash"));
        remove.addActionListener(e -> removeSelected());

        JButton up = new JButton("↑");
        up.setToolTipText(ModrinthStrings.get("instance.servers.up"));
        up.addActionListener(e -> move(-1));

        JButton down = new JButton("↓");
        down.setToolTipText(ModrinthStrings.get("instance.servers.down"));
        down.addActionListener(e -> move(1));

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(4), 0));
        south.setOpaque(false);
        south.add(add);
        south.add(remove);
        south.add(up);
        south.add(down);
        setSouth(south);
    }

    public void onShown() {
        Instance instance = instanceSource.get();
        model.clear();
        if (instance != null) {
            File file = serversFile(instance);
            if (file.isFile()) {
                try {
                    for (NBTServer server : NBTServer.loadSet(file)) {
                        model.addElement(server);
                    }
                } catch (IOException e) {
                    log.warn("Could not read {}", file, e);
                }
            }
        }
        showCurrentCard();
    }

    private void addServer() {
        Instance instance = instanceSource.get();
        if (instance == null) {
            return;
        }
        String name = Alert.showInputQuestion(
                ModrinthStrings.get("instance.servers.add"), ModrinthStrings.get("instance.servers.name-prompt"));
        if (StringUtils.isBlank(name)) {
            return;
        }
        String address = Alert.showInputQuestion(
                ModrinthStrings.get("instance.servers.add"), ModrinthStrings.get("instance.servers.address-prompt"));
        if (StringUtils.isBlank(address)) {
            return;
        }
        model.addElement(new NBTServer(parseAddress(name, address)));
        save(instance);
    }

    private void removeSelected() {
        Instance instance = instanceSource.get();
        int index = list.getSelectedIndex();
        if (instance == null || index < 0) {
            return;
        }
        model.remove(index);
        save(instance);
    }

    private void move(int delta) {
        Instance instance = instanceSource.get();
        int index = list.getSelectedIndex();
        int target = index + delta;
        if (instance == null || index < 0 || target < 0 || target >= model.size()) {
            return;
        }
        NBTServer server = model.remove(index);
        model.add(target, server);
        list.setSelectedIndex(target);
        save(instance);
    }

    private void save(Instance instance) {
        File file = serversFile(instance);
        LinkedHashSet<NBTServer> set = new LinkedHashSet<>();
        for (int i = 0; i < model.size(); i++) {
            set.add(model.get(i));
        }
        try {
            NBTServer.saveSet(set, file);
        } catch (IOException e) {
            log.warn("Could not write {}", file, e);
            Alert.showError(ModrinthStrings.get("error.title"), String.valueOf(e.getMessage()));
        }
        showCurrentCard();
    }

    private void showCurrentCard() {
        cards.show(center, model.isEmpty() ? CARD_EMPTY : CARD_LIST);
    }

    private static Server parseAddress(String name, String fullAddress) {
        String address = fullAddress.trim();
        int port = Server.DEFAULT_PORT;
        int idx = address.lastIndexOf(':');
        if (idx > 0 && idx < address.length() - 1) {
            try {
                port = Integer.parseInt(address.substring(idx + 1));
                address = address.substring(0, idx);
            } catch (NumberFormatException ignored) {
                // not "host:port" after all - keep the address exactly as typed
            }
        }
        return new Server(name, address, port);
    }

    private static File serversFile(Instance instance) {
        return new File(instance.getGameDir(), "servers.dat");
    }
}
