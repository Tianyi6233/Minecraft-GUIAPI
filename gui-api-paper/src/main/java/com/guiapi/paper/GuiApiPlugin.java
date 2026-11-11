package com.guiapi.paper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.guiapi.paper.net.Protocol;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/**
 * The GuiApi server plugin. Other plugins should NOT depend on this class directly;
 * use {@link GuiApi#get()} instead.
 */
public final class GuiApiPlugin extends JavaPlugin implements PluginMessageListener, Listener {
    private GuiApi api;

    @Override
    public void onEnable() {
        api = new GuiApi(this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("GuiApi ready on channel " + Protocol.CHANNEL);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, Protocol.CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, Protocol.CHANNEL, this);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        api.removePlayer(event.getPlayer().getUniqueId());
    }

    /** Called on a Netty worker thread — re-dispatch everything on the main thread. */
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!Protocol.CHANNEL.equals(channel)) return;
        String jsonText = api.channel.feed(player.getUniqueId(), message);
        if (jsonText == null) return;
        JsonObject json;
        try {
            json = JsonParser.parseString(jsonText).getAsJsonObject();
        } catch (RuntimeException e) {
            getLogger().warning("Invalid GuiApi message from " + player.getName() + ": " + e.getMessage());
            return;
        }
        String type = json.has("type") ? json.get("type").getAsString() : "";
        Bukkit.getScheduler().runTask(this, () -> dispatch(player, type, json));
    }

    private void dispatch(Player player, String type, JsonObject json) {
        switch (type) {
            case Protocol.TYPE_READY -> api.markReady(player,
                    json.has("mod") ? json.get("mod").getAsString() : null);
            case Protocol.TYPE_KEY -> {
                String key = json.has("key") ? json.get("key").getAsString() : "";
                api.handleKey(player, key);
            }
            case Protocol.TYPE_EVENT -> {
                String window = json.has("window") ? json.get("window").getAsString() : "";
                String widget = json.has("widget") ? json.get("widget").getAsString() : "";
                String action = json.has("action") ? json.get("action").getAsString() : "";
                com.google.gson.JsonElement value = json.get("value");
                api.handleEvent(player, window, widget, action, value);
            }
            default -> { /* ignore unknown types for forward compatibility */ }
        }
    }
}
