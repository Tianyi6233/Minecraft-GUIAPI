package com.guiapi.paper.net;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Splits outgoing messages into &lt;16 KiB frames (the plugin-message limit is ~32 KiB)
 * and reassembles incoming frames. Frame layout:
 * <pre>'G' 'A' version(1) transferId(2, BE) chunkIndex(2) chunkCount(2) payload...</pre>
 */
public final class ChunkedChannel {
    private static final int HEADER = 9;
    private static final int SLICE = 16000;
    private static final int MAX_ASSEMBLED = 4 * 1024 * 1024;
    private static final long FRAME_TIMEOUT_MS = 10_000;

    private final JavaPlugin plugin;
    private final AtomicInteger nextTransferId = new AtomicInteger(1);
    private final Map<UUID, Map<Short, ChunkSet>> incoming = new ConcurrentHashMap<>();

    public ChunkedChannel(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(Player player, String json) {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        int count = Math.max(1, (payload.length + SLICE - 1) / SLICE);
        if (count > Short.MAX_VALUE) throw new IllegalArgumentException("message too large");
        short transferId = (short) (nextTransferId.getAndIncrement() & 0x7FFF);
        for (int i = 0; i < count; i++) {
            int start = i * SLICE;
            int len = Math.min(SLICE, payload.length - start);
            byte[] frame = new byte[HEADER + len];
            frame[0] = 'G';
            frame[1] = 'A';
            frame[2] = Protocol.VERSION;
            frame[3] = (byte) (transferId >> 8);
            frame[4] = (byte) transferId;
            frame[5] = (byte) (i >> 8);
            frame[6] = (byte) i;
            frame[7] = (byte) (count >> 8);
            frame[8] = (byte) count;
            System.arraycopy(payload, start, frame, HEADER, len);
            player.sendPluginMessage(plugin, Protocol.CHANNEL, frame);
        }
    }

    /** @return the assembled message, or null if more chunks are needed / the frame was invalid. */
    public String feed(UUID playerId, byte[] frame) {
        if (frame.length < HEADER || frame[0] != 'G' || frame[1] != 'A') return null;
        short transferId = (short) (((frame[3] & 0xFF) << 8) | (frame[4] & 0xFF));
        int index = ((frame[5] & 0xFF) << 8) | (frame[6] & 0xFF);
        int count = ((frame[7] & 0xFF) << 8) | (frame[8] & 0xFF);
        if (count <= 0 || index >= count) return null;

        Map<Short, ChunkSet> byPlayer = incoming.computeIfAbsent(playerId, k -> new HashMap<>());
        synchronized (byPlayer) {
            long now = System.currentTimeMillis();
            byPlayer.values().removeIf(set -> now - set.createdAt > FRAME_TIMEOUT_MS);
            ChunkSet set = byPlayer.computeIfAbsent(transferId, k -> new ChunkSet(count));
            if (set.count != count) {
                byPlayer.remove(transferId);
                return null;
            }
            byte[] slice = new byte[frame.length - HEADER];
            System.arraycopy(frame, HEADER, slice, 0, slice.length);
            set.chunks[index] = slice;
            set.received++;
            if (set.received < set.count) return null;
            byPlayer.remove(transferId);

            int total = 0;
            for (byte[] chunk : set.chunks) {
                if (chunk == null) return null;
                total += chunk.length;
            }
            if (total > MAX_ASSEMBLED) return null;
            byte[] all = new byte[total];
            int pos = 0;
            for (byte[] chunk : set.chunks) {
                System.arraycopy(chunk, 0, all, pos, chunk.length);
                pos += chunk.length;
            }
            return new String(all, StandardCharsets.UTF_8);
        }
    }

    public void discard(UUID playerId) {
        incoming.remove(playerId);
    }

    private static final class ChunkSet {
        final int count;
        final byte[][] chunks;
        final long createdAt = System.currentTimeMillis();
        int received;

        ChunkSet(int count) {
            this.count = count;
            this.chunks = new byte[count][];
        }
    }
}
