package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.minecraft.NBTServer;
import net.legacylauncher.minecraft.Server;
import net.legacylauncher.minecraft.ping.ServerPinger;
import net.legacylauncher.minecraft.ping.ServerStatus;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.swing.extended.BackdropPanel;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The instance's own {@code servers.dat} - the multiplayer list Minecraft itself reads,
 * edited straight from the launcher instead of only from inside the game.
 */
@Slf4j
public class ServersPanel extends BackdropPanel {
    private static final String CARD_LIST = "list";
    private static final String CARD_EMPTY = "empty";
    private static final long PING_TIMEOUT_SECONDS = 6;

    private final Supplier<Instance> instanceSource;
    private final DefaultListModel<NBTServer> model = new DefaultListModel<>();
    private final JList<NBTServer> list = new JList<>(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);

    /**
     * What the last ping of each server came back with, so the list can show it without
     * pinging again on every repaint. Rebuilt from scratch each time the tab is shown.
     */
    private final Map<NBTServer, PingState> pingStates = new HashMap<>();

    public ServersPanel(Supplier<Instance> instanceSource) {
        this.instanceSource = instanceSource;
        setVgap(SwingUtil.magnify(8));

        list.setCellRenderer((l, value, index, isSelected, cellHasFocus) -> {
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(true);
            row.setBorder(BorderFactory.createEmptyBorder(
                    SwingUtil.magnify(4), SwingUtil.magnify(8), SwingUtil.magnify(4), SwingUtil.magnify(8)));
            row.setBackground(isSelected ? l.getSelectionBackground() : l.getBackground());

            JLabel name = new JLabel(value.getName() + "  —  " + value.getFullAddress());
            name.setForeground(isSelected ? l.getSelectionForeground() : l.getForeground());
            row.add(name, BorderLayout.NORTH);

            JLabel status = new JLabel(describe(pingStates.get(value)));
            status.setForeground(isSelected ? l.getSelectionForeground() : l.getForeground());
            if (!isSelected) {
                status.setEnabled(false);
            }
            row.add(status, BorderLayout.SOUTH);

            return row;
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

        JButton refresh = new JButton(ModrinthStrings.get("refresh"));
        refresh.setIcon(Images.getIcon16("refresh"));
        refresh.addActionListener(e -> pingAll());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, SwingUtil.magnify(4), 0));
        south.setOpaque(false);
        south.add(add);
        south.add(remove);
        south.add(up);
        south.add(down);
        south.add(refresh);
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
        pingAll();
    }

    /**
     * Pings every server in the list, without making the user leave the launcher to find
     * out who's online - the same thing joining would tell them, minus the wait.
     */
    private void pingAll() {
        pingStates.clear();
        for (int i = 0; i < model.size(); i++) {
            NBTServer server = model.get(i);
            pingStates.put(server, PingState.PENDING);
            // Blocking socket I/O does not reliably respond to interruption, so a hung DNS
            // lookup or connect() could otherwise wedge the ping forever; the timeout here
            // guarantees the UI moves on even if the underlying thread is still stuck.
            AsyncThread.completableTimeout(PING_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS,
                    () -> ServerPinger.ping(server.getAddress(), server.getPort())
            ).whenComplete((status, error) -> {
                PingState result = error == null ? new PingState(status) : PingState.FAILED;
                if (error != null) {
                    log.debug("Could not ping {}: {}", server.getFullAddress(), error.toString());
                }
                SwingUtil.later(() -> {
                    pingStates.put(server, result);
                    list.repaint();
                });
            });
        }
        list.repaint();
    }

    private static String describe(PingState state) {
        if (state == null || state == PingState.PENDING) {
            return ModrinthStrings.get("instance.servers.pinging");
        }
        if (state.status == null) {
            return ModrinthStrings.get("instance.servers.offline");
        }
        ServerStatus status = state.status;
        StringBuilder text = new StringBuilder();
        text.append(status.getOnlinePlayers()).append('/').append(status.getMaxPlayers())
                .append(' ').append(ModrinthStrings.get("instance.servers.players"))
                .append("  ·  ").append(status.getLatencyMs()).append(" ms");
        String motd = status.getMotd() == null ? "" : status.getMotd().trim().replace('\n', ' ');
        // legacy servers often bake color/formatting codes straight into the MOTD text
        // instead of using the chat component's structured fields
        motd = motd.replaceAll("§.", "").trim();
        if (!motd.isEmpty()) {
            text.append("  ·  ").append(motd);
        }
        return text.toString();
    }

    /**
     * What {@link #pingAll} found out about one server: {@link #PENDING} while the ping is
     * still in flight, {@link #FAILED} when the server did not answer at all, or a real
     * {@link ServerStatus} otherwise.
     */
    private static final class PingState {
        static final PingState PENDING = new PingState(null);
        static final PingState FAILED = new PingState(null);

        private final ServerStatus status;

        private PingState(ServerStatus status) {
            this.status = status;
        }
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
