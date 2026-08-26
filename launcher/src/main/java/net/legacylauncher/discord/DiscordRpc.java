package net.legacylauncher.discord;

import com.google.gson.JsonObject;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.OS;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A minimal Discord Rich Presence client - just enough of Discord's local IPC protocol to
 * show what's running in the launcher on your profile.
 * <p>
 * Talks to Discord over a named pipe ({@code \\.\pipe\discord-ipc-0} through {@code -9}),
 * the same channel Discord's own SDK uses; there is no official Java client for it. Windows
 * only for now, since that is what this fork ships. Every failure here - Discord not
 * running being the normal case, not an error - is swallowed and logged at debug level:
 * this is a nice-to-have, never something that should get in the way of playing.
 */
@Slf4j
public final class DiscordRpc {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;

    private WinNT.HANDLE pipe;
    private final String clientId;

    public DiscordRpc(String clientId) {
        this.clientId = clientId;
    }

    /**
     * @return whether a pipe was found and the handshake sent - not a guarantee Discord
     * accepted it, just that this is worth trying to keep updating
     */
    public synchronized boolean connect() {
        if (!OS.WINDOWS.isCurrent()) {
            return false;
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            return false;
        }
        close();
        try {
            for (int i = 0; i < 10; i++) {
                WinNT.HANDLE handle = Kernel32.INSTANCE.CreateFile(
                        "\\\\.\\pipe\\discord-ipc-" + i,
                        WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                        0, null, WinNT.OPEN_EXISTING, 0, null);
                if (handle != null && !handle.equals(WinBase.INVALID_HANDLE_VALUE)) {
                    pipe = handle;
                    break;
                }
            }
            if (pipe == null) {
                return false;
            }
            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", clientId);
            write(OP_HANDSHAKE, handshake);
            // one best-effort read for Discord's own READY dispatch - if nothing comes
            // back the pipe is probably dead, and every SET_ACTIVITY after this will just
            // silently fail the same way, which is fine
            read();
            return true;
        } catch (Throwable t) {
            log.debug("Could not connect to Discord: {}", t.toString());
            close();
            return false;
        }
    }

    /**
     * @param state    what to show - the instance name, normally
     * @param sinceMs  epoch millis the activity started, for the "elapsed" clock
     */
    public synchronized void setActivity(String state, long sinceMs) {
        if (pipe == null) {
            return;
        }
        try {
            JsonObject activity = new JsonObject();
            activity.addProperty("state", state);
            activity.addProperty("details", "Legism");
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", sinceMs);
            activity.add("timestamps", timestamps);

            JsonObject args = new JsonObject();
            args.addProperty("pid", currentPid());
            args.add("activity", activity);

            JsonObject frame = new JsonObject();
            frame.addProperty("cmd", "SET_ACTIVITY");
            frame.add("args", args);
            frame.addProperty("nonce", UUID.randomUUID().toString());
            write(OP_FRAME, frame);
        } catch (Throwable t) {
            log.debug("Could not update Discord activity: {}", t.toString());
            close();
        }
    }

    public synchronized void clearActivity() {
        if (pipe == null) {
            return;
        }
        try {
            JsonObject args = new JsonObject();
            args.addProperty("pid", currentPid());

            JsonObject frame = new JsonObject();
            frame.addProperty("cmd", "SET_ACTIVITY");
            frame.add("args", args);
            frame.addProperty("nonce", UUID.randomUUID().toString());
            write(OP_FRAME, frame);
        } catch (Throwable t) {
            log.debug("Could not clear Discord activity: {}", t.toString());
        }
    }

    public synchronized void close() {
        if (pipe != null) {
            try {
                Kernel32.INSTANCE.CloseHandle(pipe);
            } catch (Throwable ignored) {
            }
            pipe = null;
        }
    }

    private void write(int opcode, JsonObject payload) {
        byte[] json = payload.toString().getBytes(StandardCharsets.UTF_8);
        byte[] buffer = new byte[8 + json.length];
        writeIntLE(buffer, 0, opcode);
        writeIntLE(buffer, 4, json.length);
        System.arraycopy(json, 0, buffer, 8, json.length);
        IntByReference written = new IntByReference();
        Kernel32.INSTANCE.WriteFile(pipe, buffer, buffer.length, written, null);
    }

    /**
     * One best-effort, non-blocking-ish read of whatever Discord already sent back. Named
     * pipes here are opened in blocking mode, so this only exists right after the
     * handshake, where Discord is expected to answer quickly; nothing else calls it.
     */
    private void read() {
        byte[] header = new byte[8];
        IntByReference readCount = new IntByReference();
        if (!Kernel32.INSTANCE.ReadFile(pipe, header, 8, readCount, null) || readCount.getValue() < 8) {
            return;
        }
        int length = readIntLE(header, 4);
        if (length > 0 && length < 1 << 20) {
            byte[] body = new byte[length];
            Kernel32.INSTANCE.ReadFile(pipe, body, length, readCount, null);
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

    private static long currentPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int at = name.indexOf('@');
        try {
            return Long.parseLong(at > 0 ? name.substring(0, at) : name);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
