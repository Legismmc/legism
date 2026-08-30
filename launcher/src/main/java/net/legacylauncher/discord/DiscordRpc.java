package net.legacylauncher.discord;

import com.google.gson.JsonObject;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.OS;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A minimal Discord Rich Presence client - just enough of Discord's local IPC protocol to
 * show what's running in the launcher on your profile.
 * <p>
 * Discord listens on a local channel named {@code discord-ipc-0} through {@code -9}, which
 * is a named pipe on Windows and a Unix domain socket everywhere else; the wire format on
 * top is identical, so only the transport differs. There is no official Java client for
 * any of it.
 * <p>
 * Every failure here - Discord simply not running being the normal case, not an error - is
 * swallowed and logged at debug level: this is a nice-to-have, and must never get in the
 * way of playing.
 */
@Slf4j
public final class DiscordRpc {
    /**
     * The art asset to show beside the status. Uploaded under the Discord application's
     * Rich Presence -> Art Assets with exactly this name.
     */
    private static final String LARGE_IMAGE_KEY = "logo";

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;

    /**
     * Where to look for the socket on Linux and macOS. Discord puts it straight into the
     * runtime directory, but the sandboxed builds people actually install - Flatpak, Snap -
     * each nest it somewhere of their own, and a client that only checks the plain path
     * silently does nothing for those users.
     */
    private static final String[] UNIX_SUBDIRECTORIES = {
            "",
            "app/com.discordapp.Discord/",
            "app/com.discordapp.DiscordCanary/",
            "snap.discord/",
            "snap.discord-canary/",
            ".flatpak/dev.vencord.Vesktop/xdg-run/",
    };

    private final String clientId;
    private Transport transport;

    public DiscordRpc(String clientId) {
        this.clientId = clientId;
    }

    /**
     * @return whether a channel was found and the handshake sent - not a guarantee Discord
     * accepted it, just that this is worth trying to keep updating
     */
    public synchronized boolean connect() {
        if (clientId == null || clientId.trim().isEmpty()) {
            return false;
        }
        close();
        try {
            transport = openTransport();
            if (transport == null) {
                return false;
            }
            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", clientId);
            write(OP_HANDSHAKE, handshake);
            // one best-effort read for Discord's own READY dispatch - if nothing comes
            // back the channel is probably dead, and every SET_ACTIVITY after this will
            // just silently fail the same way, which is fine
            readReady();
            return true;
        } catch (Throwable t) {
            log.debug("Could not connect to Discord: {}", t.toString());
            close();
            return false;
        }
    }

    private Transport openTransport() {
        if (OS.WINDOWS.isCurrent()) {
            return WindowsPipeTransport.open();
        }
        return UnixSocketTransport.open();
    }

    /**
     * @param instanceName what the player called the instance
     * @param version      the Minecraft version it runs, shown underneath
     * @param sinceMs      epoch millis the activity started, for the "elapsed" clock
     */
    public synchronized void setActivity(String instanceName, String version, long sinceMs) {
        if (transport == null) {
            return;
        }
        try {
            JsonObject activity = new JsonObject();
            // Discord already prints the application's name above all of this, so putting
            // "Legism" in here again just said it twice. The instance and the version it
            // runs are what the reader does not already know.
            activity.addProperty("details", blankToNull(instanceName) == null ? "Minecraft" : instanceName);
            // an instance named after its own version - the default - would otherwise
            // repeat itself on the next line
            String second = blankToNull(version);
            if (second != null && !second.equalsIgnoreCase(instanceName)) {
                activity.addProperty("state", second);
            }
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", sinceMs);
            activity.add("timestamps", timestamps);

            // Names an art asset uploaded to the Discord application. Discord simply shows
            // no picture when the key is not there, so this can be sent before the asset
            // exists and starts working the moment one by this name is uploaded - no
            // release needed to turn it on.
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", LARGE_IMAGE_KEY);
            assets.addProperty("large_text", "Legism");
            activity.add("assets", assets);

            JsonObject args = new JsonObject();
            args.addProperty("pid", currentPid());
            args.add("activity", activity);

            write(OP_FRAME, activityFrame(args));
        } catch (Throwable t) {
            log.debug("Could not update Discord activity: {}", t.toString());
            close();
        }
    }

    public synchronized void clearActivity() {
        if (transport == null) {
            return;
        }
        try {
            JsonObject args = new JsonObject();
            args.addProperty("pid", currentPid());
            write(OP_FRAME, activityFrame(args));
        } catch (Throwable t) {
            log.debug("Could not clear Discord activity: {}", t.toString());
        }
    }

    private static JsonObject activityFrame(JsonObject args) {
        JsonObject frame = new JsonObject();
        frame.addProperty("cmd", "SET_ACTIVITY");
        frame.add("args", args);
        frame.addProperty("nonce", UUID.randomUUID().toString());
        return frame;
    }

    public synchronized void close() {
        if (transport != null) {
            transport.close();
            transport = null;
        }
    }

    private void write(int opcode, JsonObject payload) throws IOException {
        byte[] json = payload.toString().getBytes(StandardCharsets.UTF_8);
        byte[] buffer = new byte[8 + json.length];
        writeIntLE(buffer, 0, opcode);
        writeIntLE(buffer, 4, json.length);
        System.arraycopy(json, 0, buffer, 8, json.length);
        transport.write(buffer);
    }

    /**
     * One best-effort read of whatever Discord already sent back. The channel is blocking,
     * so this only runs right after the handshake, where Discord is expected to answer
     * quickly; nothing else calls it.
     */
    private void readReady() throws IOException {
        byte[] header = new byte[8];
        if (transport.read(header, 8) < 8) {
            return;
        }
        int length = readIntLE(header, 4);
        if (length > 0 && length < 1 << 20) {
            transport.read(new byte[length], length);
        }
    }

    private static void writeIntLE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) value;
        buf[offset + 1] = (byte) (value >> 8);
        buf[offset + 2] = (byte) (value >> 16);
        buf[offset + 3] = (byte) (value >> 24);
    }

    private static int readIntLE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF)
                | ((buf[offset + 1] & 0xFF) << 8)
                | ((buf[offset + 2] & 0xFF) << 16)
                | ((buf[offset + 3] & 0xFF) << 24);
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static long currentPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int at = name.indexOf('@');
        try {
            return Long.parseLong(at > 0 ? name.substring(0, at) : name);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * The half of the protocol that differs per platform. Discord numbers its channels 0
     * through 9 and uses the first free one, so every implementation has to try all ten.
     */
    private interface Transport {
        void write(byte[] data) throws IOException;

        /**
         * @return how many bytes were actually read, which may be short of what was asked
         */
        int read(byte[] buffer, int length) throws IOException;

        void close();
    }

    private static final class WindowsPipeTransport implements Transport {
        private final WinNT.HANDLE pipe;

        private WindowsPipeTransport(WinNT.HANDLE pipe) {
            this.pipe = pipe;
        }

        static Transport open() {
            for (int i = 0; i < 10; i++) {
                WinNT.HANDLE handle = Kernel32.INSTANCE.CreateFile(
                        "\\\\.\\pipe\\discord-ipc-" + i,
                        WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                        0, null, WinNT.OPEN_EXISTING, 0, null);
                if (handle != null && !handle.equals(WinBase.INVALID_HANDLE_VALUE)) {
                    return new WindowsPipeTransport(handle);
                }
            }
            return null;
        }

        @Override
        public void write(byte[] data) {
            Kernel32.INSTANCE.WriteFile(pipe, data, data.length, new IntByReference(), null);
        }

        @Override
        public int read(byte[] buffer, int length) {
            IntByReference readCount = new IntByReference();
            if (!Kernel32.INSTANCE.ReadFile(pipe, buffer, length, readCount, null)) {
                return -1;
            }
            return readCount.getValue();
        }

        @Override
        public void close() {
            try {
                Kernel32.INSTANCE.CloseHandle(pipe);
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class UnixSocketTransport implements Transport {
        private final AFUNIXSocket socket;
        private final OutputStream out;
        private final InputStream in;

        private UnixSocketTransport(AFUNIXSocket socket) throws IOException {
            this.socket = socket;
            this.out = socket.getOutputStream();
            this.in = socket.getInputStream();
        }

        static Transport open() {
            for (File directory : candidateDirectories()) {
                for (int i = 0; i < 10; i++) {
                    File candidate = new File(directory, "discord-ipc-" + i);
                    if (!candidate.exists()) {
                        continue;
                    }
                    try {
                        return new UnixSocketTransport(
                                AFUNIXSocket.connectTo(AFUNIXSocketAddress.of(candidate)));
                    } catch (Throwable t) {
                        log.debug("Discord socket {} did not accept us: {}", candidate, t.toString());
                    }
                }
            }
            return null;
        }

        private static List<File> candidateDirectories() {
            List<File> roots = new ArrayList<>();
            addIfSet(roots, System.getenv("XDG_RUNTIME_DIR"));
            addIfSet(roots, System.getenv("TMPDIR"));
            addIfSet(roots, System.getenv("TMP"));
            addIfSet(roots, System.getenv("TEMP"));
            roots.add(new File("/tmp"));

            List<File> directories = new ArrayList<>();
            for (File root : roots) {
                for (String subdirectory : UNIX_SUBDIRECTORIES) {
                    directories.add(subdirectory.isEmpty() ? root : new File(root, subdirectory));
                }
            }
            return directories;
        }

        private static void addIfSet(List<File> roots, String path) {
            if (path != null && !path.trim().isEmpty()) {
                roots.add(new File(path));
            }
        }

        @Override
        public void write(byte[] data) throws IOException {
            out.write(data);
            out.flush();
        }

        @Override
        public int read(byte[] buffer, int length) throws IOException {
            return in.read(buffer, 0, length);
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
