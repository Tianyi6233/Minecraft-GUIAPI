package com.guiapi.client.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.guiapi.client.gui.GuiManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles the guiapi:main channel: chunk reassembly/splitting
 * (same frame format as the server) and message dispatch.
 */
public final class ClientNet {
    /** Plugin channel shared with the Paper plugin. */
    public static final ResourceLocation CHANNEL = new ResourceLocation("guiapi", "main");
    private static final int HEADER = 9;
    private static final int SLICE = 16000;
    private static int nextTransferId = 1;

    private ClientNet() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, responseSender) -> {
            byte[] frame = new byte[buf.readableBytes()];
            buf.readBytes(frame);
            client.execute(() -> handle(frame));
        });
    }

    // ------------------------------------------------------------ receive

    private static final Map<Short, byte[][]> chunks = new HashMap<>();
    private static final Map<Short, Integer> chunkCounts = new HashMap<>();
    private static final Map<Short, Integer> chunkReceived = new HashMap<>();

    private static void handle(byte[] frame) {
        if (frame.length < HEADER || frame[0] != 'G' || frame[1] != 'A') return;
        short transferId = (short) (((frame[3] & 0xFF) << 8) | (frame[4] & 0xFF));
        int index = ((frame[5] & 0xFF) << 8) | (frame[6] & 0xFF);
        int count = ((frame[7] & 0xFF) << 8) | (frame[8] & 0xFF);
        if (count <= 0 || index >= count) return;

        byte[][] set = chunks.computeIfAbsent(transferId, k -> new byte[count][]);
        if (set.length != count) {
            discard(transferId);
            return;
        }
        byte[] slice = new byte[frame.length - HEADER];
        System.arraycopy(frame, HEADER, slice, 0, slice.length);
        set[index] = slice;
        chunkCounts.put(transferId, count);
        int received = chunkReceived.getOrDefault(transferId, 0) + 1;
        chunkReceived.put(transferId, received);
        if (received < count) return;

        int total = 0;
        for (byte[] part : set) if (part != null) total += part.length;
        byte[] all = new byte[total];
        int pos = 0;
        for (byte[] part : set) {
            System.arraycopy(part, 0, all, pos, part.length);
            pos += part.length;
        }
        discard(transferId);

        try {
            JsonObject json = JsonParser.parseString(new String(all, StandardCharsets.UTF_8)).getAsJsonObject();
            GuiManager.handleMessage(json);
        } catch (RuntimeException e) {
            // ignore malformed message
        }
    }

    private static void discard(short transferId) {
        chunks.remove(transferId);
        chunkCounts.remove(transferId);
        chunkReceived.remove(transferId);
    }

    // ------------------------------------------------------------ send

    public static void send(JsonObject json) {
        byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);
        int count = Math.max(1, (payload.length + SLICE - 1) / SLICE);
        short transferId = (short) (nextTransferId++ & 0x7FFF);
        for (int i = 0; i < count; i++) {
            int start = i * SLICE;
            int len = Math.min(SLICE, payload.length - start);
            byte[] frame = new byte[HEADER + len];
            frame[0] = 'G';
            frame[1] = 'A';
            frame[2] = 1;
            frame[3] = (byte) (transferId >> 8);
            frame[4] = (byte) transferId;
            frame[5] = (byte) (i >> 8);
            frame[6] = (byte) i;
            frame[7] = (byte) (count >> 8);
            frame[8] = (byte) count;
            System.arraycopy(payload, start, frame, HEADER, len);
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBytes(frame);
            ClientPlayNetworking.send(CHANNEL, buf);
        }
    }

    private static JsonObject base(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("v", 1);
        json.addProperty("type", type);
        return json;
    }

    public static void sendReady() {
        JsonObject json = base("ready");
        // 上报模组版本：服务端据此拒绝版本过低的客户端
        String version = FabricLoader.getInstance()
                .getModContainer("guiapi")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        if (!version.isEmpty()) json.addProperty("mod", version);
        send(json);
    }

    public static void sendKey(String key) {
        JsonObject json = base("key");
        json.addProperty("key", key);
        send(json);
    }

    public static void sendClick(String window, String widget) {
        sendEvent(window, widget, "click", null);
    }

    public static void sendChange(String window, String widget, double value) {
        JsonObject json = base("event");
        json.addProperty("window", window);
        json.addProperty("widget", widget);
        json.addProperty("action", "change");
        json.addProperty("value", value);
        send(json);
    }

    public static void sendToggle(String window, String widget, boolean value) {
        JsonObject json = base("event");
        json.addProperty("window", window);
        json.addProperty("widget", widget);
        json.addProperty("action", "toggle");
        json.addProperty("value", value);
        send(json);
    }

    public static void sendSelect(String window, String widget, int index, String option) {
        JsonObject json = base("event");
        json.addProperty("window", window);
        json.addProperty("widget", widget);
        json.addProperty("action", "select");
        json.addProperty("value", index);
        json.addProperty("option", option);
        send(json);
    }

    /** Sends the final content of a text field (on Enter or focus loss). */
    public static void sendText(String window, String widget, String text) {
        JsonObject json = base("event");
        json.addProperty("window", window);
        json.addProperty("widget", widget);
        json.addProperty("action", "text");
        json.addProperty("value", text);
        send(json);
    }

    public static void sendClose(String window) {
        sendEvent(window, window, "close", null);
    }

    /** Sent when Esc / the close button hits a window that refuses direct closing. */
    public static void sendEscape(String window) {
        sendEvent(window, window, "esc", null);
    }

    private static void sendEvent(String window, String widget, String action, String ignored) {
        JsonObject json = base("event");
        json.addProperty("window", window);
        json.addProperty("widget", widget);
        json.addProperty("action", action);
        send(json);
    }
}
