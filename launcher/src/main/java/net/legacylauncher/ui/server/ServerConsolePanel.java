package net.legacylauncher.ui.server;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.server.LocalServerProcess;
import net.legacylauncher.server.ServerCoreDownload;
import net.legacylauncher.server.ServerCoreInstaller;
import net.legacylauncher.server.ServerInstance;
import net.legacylauncher.server.ServerInstanceManager;
import net.legacylauncher.server.ServerProcessRegistry;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.util.SwingUtil;
import net.legacylauncher.util.async.AsyncThread;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Start/stop button, status line, live console output and a command box for one local
 * server - the hosting equivalent of what {@code LoggerFrame} is for the launcher's own
 * log, but per server and backed by a real child process instead of the log buffer.
 */
@Slf4j
public class ServerConsolePanel extends JPanel {
    private final ServerInstanceManager manager;
    private final ServerInstance server;
    private final LocalServerProcess process;

    private final JTextArea console = new JTextArea();
    private final JLabel status = new JLabel();
    private final JButton startStop = new JButton();
    private final JTextField commandField = new JTextField();

    public ServerConsolePanel(ServerInstanceManager manager, ServerInstance server) {
        super(new BorderLayout(0, SwingUtil.magnify(8)));
        this.manager = manager;
        this.server = server;
        this.process = ServerProcessRegistry.get(server);
        setBorder(BorderFactory.createEmptyBorder(
                SwingUtil.magnify(8), SwingUtil.magnify(8), SwingUtil.magnify(8), SwingUtil.magnify(8)));

        JPanel top = new JPanel(new BorderLayout(SwingUtil.magnify(8), 0));
        startStop.addActionListener(e -> toggle());
        top.add(startStop, BorderLayout.WEST);
        status.setHorizontalAlignment(SwingConstants.LEFT);
        top.add(status, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, SwingUtil.magnify(12)));
        console.setLineWrap(true);
        console.setWrapStyleWord(true);
        add(new JScrollPane(console), BorderLayout.CENTER);

        commandField.putClientProperty("JTextField.placeholderText",
                ModrinthStrings.get("server.console.command-hint"));
        commandField.setEnabled(false);
        commandField.addActionListener(e -> sendCommand());
        add(commandField, BorderLayout.SOUTH);

        if (process.isRunning()) {
            process.attachListener(consoleListener());
            updateState(true);
        } else {
            updateState(false);
        }
    }

    private LocalServerProcess.ConsoleListener consoleListener() {
        return new LocalServerProcess.ConsoleListener() {
            @Override
            public void onLine(String line) {
                SwingUtil.later(() -> appendLine(line));
            }

            @Override
            public void onStopped(int exitCode) {
                SwingUtil.later(() -> {
                    appendLine("[launcher] process ended, exit code " + exitCode);
                    updateState(false);
                });
            }
        };
    }

    private void toggle() {
        if (process.isRunning()) {
            process.stop();
        } else {
            start();
        }
    }

    private void start() {
        startStop.setEnabled(false);
        status.setText(ModrinthStrings.get("server.console.starting"));
        AsyncThread.execute(() -> {
            try {
                ensureCore();
                if (!acceptEula()) {
                    SwingUtil.later(() -> updateState(false));
                    return;
                }
                ensurePropertiesPort();
                process.start(server.getXmx(), consoleListener());
                server.setLastStarted(System.currentTimeMillis());
                try {
                    manager.save(server);
                } catch (IOException e) {
                    log.warn("Could not save last-started time for {}", server, e);
                }
                SwingUtil.later(() -> updateState(true));
            } catch (Exception e) {
                log.warn("Could not start local server {}", server, e);
                SwingUtil.later(() -> {
                    updateState(false);
                    Alert.showError(ModrinthStrings.get("server.console.start-failed"), e.getMessage());
                });
            }
        });
    }

    private void ensureCore() throws IOException {
        if (server.getJarFile().isFile()) {
            return;
        }
        SwingUtil.later(() -> status.setText(
                ModrinthStrings.get("server.console.downloading", server.getCore())));
        ServerCoreDownload download;
        try {
            download = server.getCore().resolveDownload(server.getCoreVersion());
        } catch (IOException e) {
            throw new IOException(ModrinthStrings.get("server.console.download-failed") + ": " + e.getMessage(), e);
        }
        ServerCoreInstaller.install(server, download);
    }

    /**
     * @return false if the user declined the EULA - the caller must not start the process
     */
    private boolean acceptEula() throws IOException {
        Properties eula = new Properties();
        if (server.getEulaFile().isFile()) {
            try (Reader in = Files.newBufferedReader(server.getEulaFile().toPath(), StandardCharsets.UTF_8)) {
                eula.load(in);
            }
        }
        if ("true".equals(eula.getProperty("eula"))) {
            return true;
        }
        boolean accepted = SwingUtil.waitAndReturn(() -> Alert.showQuestion(
                ModrinthStrings.get("server.eula.title"), ModrinthStrings.get("server.eula.message")));
        if (!accepted) {
            return false;
        }
        eula.setProperty("eula", "true");
        try (Writer out = Files.newBufferedWriter(server.getEulaFile().toPath(), StandardCharsets.UTF_8)) {
            eula.store(out, "Generated by Legism");
        }
        return true;
    }

    private void ensurePropertiesPort() throws IOException {
        Properties props = new Properties();
        if (server.getPropertiesFile().isFile()) {
            try (Reader in = Files.newBufferedReader(server.getPropertiesFile().toPath(), StandardCharsets.UTF_8)) {
                props.load(in);
            }
        }
        props.setProperty("server-port", String.valueOf(server.getPort()));
        try (Writer out = Files.newBufferedWriter(server.getPropertiesFile().toPath(), StandardCharsets.UTF_8)) {
            props.store(out, "Generated by Legism");
        }
    }

    private void sendCommand() {
        String command = commandField.getText().trim();
        if (command.isEmpty()) {
            return;
        }
        commandField.setText("");
        appendLine("> " + command);
        AsyncThread.execute(() -> {
            try {
                process.sendCommand(command);
            } catch (IOException e) {
                log.warn("Could not send command to {}", server, e);
            }
        });
    }

    private void appendLine(String line) {
        console.append(line + "\n");
        console.setCaretPosition(console.getDocument().getLength());
    }

    private void updateState(boolean running) {
        startStop.setEnabled(true);
        startStop.setText(running
                ? ModrinthStrings.get("server.console.stop")
                : ModrinthStrings.get("server.console.start"));
        status.setText(running
                ? ModrinthStrings.get("server.console.running")
                : ModrinthStrings.get("server.console.stopped"));
        commandField.setEnabled(running);
    }
}
