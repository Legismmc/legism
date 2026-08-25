package net.legacylauncher.server;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.OS;
import net.minecraft.launcher.process.JavaProcess;
import net.minecraft.launcher.process.JavaProcessLauncher;
import net.minecraft.launcher.process.JavaProcessListener;
import net.minecraft.launcher.process.PrintStreamType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Runs one local server's jar as a child process, forwarding its console output line by
 * line and accepting typed-in commands on its stdin - the server hosting equivalent of
 * {@link net.legacylauncher.minecraft.launcher.MinecraftLauncher}, minus everything that
 * pipeline does that a server jar has no use for (asset downloads, JVM arg templates,
 * account injection).
 */
@Slf4j
public class LocalServerProcess {
    public interface ConsoleListener {
        void onLine(String line);

        void onStopped(int exitCode);
    }

    private final ServerInstance server;
    private JavaProcess process;

    public LocalServerProcess(ServerInstance server) {
        this.server = server;
    }

    public boolean isRunning() {
        return process != null && process.isRunning();
    }

    public synchronized void start(String xmx, ConsoleListener listener) throws IOException {
        if (isRunning()) {
            throw new IllegalStateException("server is already running");
        }
        JavaProcessLauncher launcher = new JavaProcessLauncher(StandardCharsets.UTF_8, OS.getJavaPath(), new String[0]);
        launcher.directory(server.getFolder());
        launcher.addCommand("-Xmx" + xmx + "M");
        launcher.addCommand("-Xms" + xmx + "M");
        launcher.addCommand("-jar");
        launcher.addCommand(server.getJarFile().getAbsolutePath());
        launcher.addCommand("nogui");

        log.info("Starting local server {}: {}", server, launcher.getCommandsAsString());
        process = launcher.start();
        attachListener(listener);
    }

    /**
     * Points an already-running process at a fresh listener - used when a console view for
     * a server is reopened after having been closed, so new output has somewhere to go
     * again. Past output is not replayed.
     */
    public synchronized void attachListener(ConsoleListener listener) {
        if (process == null) {
            return;
        }
        process.safeSetExitRunnable(new JavaProcessListener() {
            @Override
            public void onJavaProcessPrint(JavaProcess p, PrintStreamType streamType, String line) {
                listener.onLine(line);
            }

            @Override
            public void onJavaProcessEnded(JavaProcess p) {
                int exitCode;
                try {
                    exitCode = p.getExitCode();
                } catch (IllegalThreadStateException e) {
                    exitCode = -1;
                }
                listener.onStopped(exitCode);
            }

            @Override
            public void onJavaProcessError(JavaProcess p, Throwable t) {
                log.warn("Local server {} reported an error", server, t);
            }
        });
    }

    /**
     * Sends one line to the server's console, e.g. {@code stop} or {@code say hi}.
     */
    public synchronized void sendCommand(String command) throws IOException {
        if (!isRunning()) {
            return;
        }
        OutputStream in = process.getRawProcess().getOutputStream();
        in.write((command + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        in.flush();
    }

    /**
     * Asks the server to shut down cleanly; falls back to killing it if it will not.
     */
    public void stop() {
        if (!isRunning()) {
            return;
        }
        try {
            sendCommand("stop");
        } catch (IOException e) {
            log.warn("Could not send stop command to {}, killing it instead", server, e);
            process.stop();
        }
    }

    public void kill() {
        if (isRunning()) {
            process.stop();
        }
    }
}
