package net.legacylauncher.ui.server;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.server.ServerInstance;
import net.legacylauncher.server.ServerInstanceManager;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.util.SwingUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

/**
 * A curated subset of {@code server.properties} - the handful of fields most people
 * actually change, rather than every one of the sixty-odd keys vanilla exposes. Everything
 * else already on disk is preserved as-is; only the fields shown here get overwritten.
 */
@Slf4j
public class ServerPropertiesPanel extends JPanel {
    private final ServerInstanceManager manager;
    private final ServerInstance server;

    private final JSpinner port = new JSpinner(new SpinnerNumberModel(25565, 1, 65535, 1));
    private final JTextField motd = new JTextField();
    private final JSpinner maxPlayers = new JSpinner(new SpinnerNumberModel(20, 1, 200, 1));
    private final JComboBox<String> difficulty = new JComboBox<>(new String[]{"peaceful", "easy", "normal", "hard"});
    private final JComboBox<String> gamemode = new JComboBox<>(new String[]{"survival", "creative", "adventure", "spectator"});
    private final JCheckBox onlineMode = new JCheckBox();
    private final JCheckBox pvp = new JCheckBox();
    private final JCheckBox whitelist = new JCheckBox();

    public ServerPropertiesPanel(ServerInstanceManager manager, ServerInstance server) {
        super(new BorderLayout());
        this.manager = manager;
        this.server = server;

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(8), SwingUtil.magnify(8), SwingUtil.magnify(8), SwingUtil.magnify(8)));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(SwingUtil.magnify(3), SwingUtil.magnify(4),
                SwingUtil.magnify(3), SwingUtil.magnify(4));
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        row = addRow(form, c, row, "server.properties.port", port);
        row = addRow(form, c, row, "server.properties.motd", motd);
        row = addRow(form, c, row, "server.properties.max-players", maxPlayers);
        row = addRow(form, c, row, "server.properties.difficulty", difficulty);
        row = addRow(form, c, row, "server.properties.gamemode", gamemode);
        row = addRow(form, c, row, "server.properties.online-mode", onlineMode);
        row = addRow(form, c, row, "server.properties.pvp", pvp);
        addRow(form, c, row, "server.properties.white-list", whitelist);

        add(form, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton save = new JButton(ModrinthStrings.get("server.properties.save"));
        save.addActionListener(e -> save());
        buttons.add(save);
        add(buttons, BorderLayout.SOUTH);

        load();
    }

    private int addRow(JPanel form, GridBagConstraints c, int row, String labelKey, java.awt.Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel(ModrinthStrings.get(labelKey) + ":"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        form.add(field, c);
        return row + 1;
    }

    private void load() {
        Properties props = readProperties();
        port.setValue(server.getPort());
        motd.setText(props.getProperty("motd", "A Minecraft Server"));
        maxPlayers.setValue(parseInt(props.getProperty("max-players"), 20));
        difficulty.setSelectedItem(props.getProperty("difficulty", "easy"));
        gamemode.setSelectedItem(props.getProperty("gamemode", "survival"));
        onlineMode.setSelected(!"false".equals(props.getProperty("online-mode", "true")));
        pvp.setSelected(!"false".equals(props.getProperty("pvp", "true")));
        whitelist.setSelected("true".equals(props.getProperty("white-list", "false")));
    }

    private Properties readProperties() {
        Properties props = new Properties();
        if (server.getPropertiesFile().isFile()) {
            try (Reader in = Files.newBufferedReader(server.getPropertiesFile().toPath(), StandardCharsets.UTF_8)) {
                props.load(in);
            } catch (IOException e) {
                log.warn("Could not read server.properties for {}", server, e);
            }
        }
        return props;
    }

    private void save() {
        try {
            Properties props = readProperties();
            server.setPort((Integer) port.getValue());
            props.setProperty("server-port", String.valueOf(server.getPort()));
            props.setProperty("motd", motd.getText());
            props.setProperty("max-players", String.valueOf(maxPlayers.getValue()));
            props.setProperty("difficulty", String.valueOf(difficulty.getSelectedItem()));
            props.setProperty("gamemode", String.valueOf(gamemode.getSelectedItem()));
            props.setProperty("online-mode", String.valueOf(onlineMode.isSelected()));
            props.setProperty("pvp", String.valueOf(pvp.isSelected()));
            props.setProperty("white-list", String.valueOf(whitelist.isSelected()));
            try (Writer out = Files.newBufferedWriter(server.getPropertiesFile().toPath(), StandardCharsets.UTF_8)) {
                props.store(out, "Generated by Legacy by tgsko");
            }
            manager.save(server);
            Alert.showMessage(ModrinthStrings.get("server.properties.save"),
                    ModrinthStrings.get("server.properties.saved"));
        } catch (IOException e) {
            log.warn("Could not save server.properties for {}", server, e);
            Alert.showError(ModrinthStrings.get("server.error.create"), e.getMessage());
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
