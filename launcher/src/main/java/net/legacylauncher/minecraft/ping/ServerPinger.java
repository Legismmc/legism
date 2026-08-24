package net.legacylauncher.minecraft.ping;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Talks the Minecraft Server List Ping protocol: connect, handshake, ask for status, ask
 * for latency, disconnect. The same handshake-then-status exchange the vanilla multiplayer
 * screen does before showing a server's MOTD and player count - unchanged since 1.7, and
 * every server answers it regardless of what protocol version the handshake claims, since
 * status is not a login attempt.
 * <p>
 * Every call blocks, so callers must stay off the Swing thread.
 */
public final class ServerPinger {
    private static final int TIMEOUT_MS = 4000;

    /**
     * A protocol version has to be sent, but servers answer a status request with their own
     * regardless of what it says - this is just what a recent client would send.
     */
    private static final int HANDSHAKE_PROTOCOL_VERSION = 767;

    private ServerPinger() {
    }

    public static ServerStatus ping(String host, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            writeHandshake(out, host, port);
            writePacket(out, new byte[]{0x00}); // status request: just the packet id, no fields

            String json = readStatusResponse(in);

            long latency = ping(out, in);

            return parse(json, latency);
        }
    }

    private static void writeHandshake(DataOutputStream out, String host, int port) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(body);
        writeVarInt(data, 0x00);
        writeVarInt(data, HANDSHAKE_PROTOCOL_VERSION);
        writeString(data, host);
        data.writeShort(port);
        writeVarInt(data, 1); // next state: status
        writePacket(out, body.toByteArray());
    }

    private static String readStatusResponse(DataInputStream in) throws IOException {
        readVarInt(in); // total packet length, unused: the body is read to its own end anyway
        int packetId = readVarInt(in);
        if (packetId != 0x00) {
            throw new IOException("unexpected packet id " + packetId + " in status response");
        }
        return readString(in);
    }

    /**
     * @return round trip time in milliseconds
     */
    private static long ping(DataOutputStream out, DataInputStream in) throws IOException {
        long sent = System.currentTimeMillis();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(body);
        writeVarInt(data, 0x01);
        data.writeLong(sent);
        writePacket(out, body.toByteArray());

        readVarInt(in); // pong packet length
        readVarInt(in); // pong packet id, expected 0x01 - not worth failing the ping over
        in.readLong(); // the echoed payload; only the round trip time is of any use here
        return System.currentTimeMillis() - sent;
    }

    private static ServerStatus parse(String json, long latency) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        String motd = "";
        if (root.has("description")) {
            motd = flatten(root.get("description"));
        }

        int online = 0;
        int max = 0;
        if (root.has("players") && root.get("players").isJsonObject()) {
            JsonObject players = root.getAsJsonObject("players");
            online = intOf(players, "online");
            max = intOf(players, "max");
        }

        String versionName = "";
        if (root.has("version") && root.get("version").isJsonObject()) {
            JsonElement name = root.getAsJsonObject("version").get("name");
            if (name != null && name.isJsonPrimitive()) {
                versionName = name.getAsString();
            }
        }

        return new ServerStatus(motd, online, max, versionName, latency);
    }

    private static int intOf(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : 0;
    }

    /**
     * A status response's {@code description} is either a plain string or a Minecraft chat
     * component - an object with its own {@code text} and a possible {@code extra} array of
     * more components. Only the text is of any use here; colour and formatting codes are
     * dropped along with everything else.
     */
    private static String flatten(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (!element.isJsonObject()) {
            return "";
        }
        JsonObject object = element.getAsJsonObject();
        StringBuilder result = new StringBuilder();
        if (object.has("text")) {
            result.append(object.get("text").getAsString());
        }
        if (object.has("extra") && object.get("extra").isJsonArray()) {
            JsonArray extra = object.getAsJsonArray("extra");
            for (JsonElement child : extra) {
                result.append(flatten(child));
            }
        }
        return result.toString();
    }

    // ---------------------------------------------------------------- wire format

    private static void writePacket(OutputStream out, byte[] body) throws IOException {
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        writeVarInt(new DataOutputStream(framed), body.length);
        framed.write(body);
        out.write(framed.toByteArray());
        out.flush();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            byte b = in.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift >= 32) {
                throw new IOException("VarInt is too big");
            }
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
