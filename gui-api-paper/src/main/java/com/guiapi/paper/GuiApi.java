package com.guiapi.paper;

import com.google.gson.JsonObject;
import com.guiapi.paper.net.ChunkedChannel;
import com.guiapi.paper.net.Protocol;
import com.guiapi.paper.widget.ImageWidget;
import com.guiapi.paper.widget.Widget;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Entry point of the GUI drawing API. Obtain it with {@link GuiApi#get()}.
 */
public final class GuiApi {
    private static GuiApi instance;

    final GuiApiPlugin plugin;
    final ChunkedChannel channel;

    /** window id -> window, per player */
    final Map<UUID, Map<String, GuiWindow>> openWindows = new ConcurrentHashMap<>();
    /** players whose client sent the "ready" handshake */
    private final Map<UUID, Long> supportedClients = new ConcurrentHashMap<>();
    /** player -> GuiApi 客户端模组版本；旧客户端不上报版本时没有这个键。 */
    private final Map<UUID, String> clientVersions = new ConcurrentHashMap<>();
    private final Map<String, byte[]> images = new ConcurrentHashMap<>();
    /** bumped every time an id is registered again, so clients only receive the current bytes */
    private final Map<String, Integer> imageVersions = new ConcurrentHashMap<>();
    /** player -> (image id -> version already delivered). Images are sent once per session. */
    private final Map<UUID, Map<String, Integer>> sentImages = new ConcurrentHashMap<>();
    /** sound id -> OGG bytes, mirroring the image pipeline so no resource pack has to be edited by hand. */
    private final Map<String, byte[]> sounds = new ConcurrentHashMap<>();
    private final Map<String, Integer> soundVersions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> sentSounds = new ConcurrentHashMap<>();
    /** optional click sound played for every widget click, so plugins keep one line per UI. */
    private volatile String clickSound;
    private volatile float clickSoundVolume = 0.7f;
    private final Map<String, Consumer<Player>> keyHandlers = new ConcurrentHashMap<>();

    GuiApi(GuiApiPlugin plugin) {
        this.plugin = plugin;
        this.channel = new ChunkedChannel(plugin);
        instance = this;
    }

    /** API entry point. Requires the GuiApi plugin to be enabled (use depend/softdepend in plugin.yml). */
    public static GuiApi get() {
        GuiApi api = instance;
        if (api == null) throw new IllegalStateException("GuiApi plugin is not enabled yet");
        return api;
    }

    // ---------------------------------------------------------------- windows

    /** Opens (or replaces) a window on the player's client. */
    public void openWindow(Player player, GuiWindow window) {
        openWindows.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(window.id(), window);
        // send every referenced image once per client session, so frequent re-renders stay cheap
        for (Widget widget : window.widgets()) {
            if (widget instanceof ImageWidget image) sendImageIfNeeded(player, image.imageId());
        }
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_OPEN_WINDOW);
        json.add("window", window.toJson());
        channel.send(player, json.toString());
    }

    /** Closes one window (or every window when windowId is null). */
    public void closeWindow(Player player, String windowId) {
        Map<String, GuiWindow> windows = openWindows.get(player.getUniqueId());
        if (windows != null) {
            if (windowId == null) windows.clear();
            else windows.remove(windowId);
        }
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_CLOSE_WINDOW);
        if (windowId != null) json.addProperty("id", windowId);
        channel.send(player, json.toString());
    }

    /** Updates properties (text, value, checked, options, selected, ...) of a widget that is already shown. */
    public void updateWidget(Player player, String windowId, String widgetId, Map<String, Object> properties) {
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_UPDATE_WIDGET);
        json.addProperty("window", windowId);
        json.addProperty("widget", widgetId);
        JsonObject props = new JsonObject();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object v = entry.getValue();
            if (v instanceof Number n) props.addProperty(entry.getKey(), n);
            else if (v instanceof Boolean b) props.addProperty(entry.getKey(), b);
            else if (v instanceof List<?> list) {
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (Object o : list) arr.add(String.valueOf(o));
                props.add(entry.getKey(), arr);
            } else props.addProperty(entry.getKey(), String.valueOf(v));
        }
        json.add("props", props);
        channel.send(player, json.toString());
    }

    // ---------------------------------------------------------------- images

    /** Registers a PNG/JPEG image under an id, usable by image widgets. */
    public void registerImage(String imageId, byte[] imageBytes) {
        images.put(imageId, imageBytes);
        imageVersions.merge(imageId, 1, Integer::sum);
    }

    /**
     * Sends a registered image to the player unless this client already received the current
     * version. Use it to warm up images that only appear later through {@code updateWidget}.
     */
    public void preloadImage(Player player, String imageId) {
        sendImageIfNeeded(player, imageId);
    }

    /** Force-sends a registered image, even when the client already has it. */
    public void sendImage(Player player, String imageId) {
        byte[] data = images.get(imageId);
        if (data == null) return;
        int version = imageVersions.getOrDefault(imageId, 1);
        sendImage(player, imageId, data);
        sentImages.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(imageId, version);
    }

    private void sendImageIfNeeded(Player player, String imageId) {
        byte[] data = images.get(imageId);
        if (data == null) return;
        int version = imageVersions.getOrDefault(imageId, 1);
        Map<String, Integer> sent = sentImages.computeIfAbsent(player.getUniqueId(),
                k -> new ConcurrentHashMap<>());
        Integer delivered = sent.get(imageId);
        if (delivered != null && delivered == version) return;
        sendImage(player, imageId, data);
        sent.put(imageId, version);
    }

    private void sendImage(Player player, String imageId, byte[] data) {
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_IMAGE);
        json.addProperty("imageId", imageId);
        json.addProperty("data", java.util.Base64.getEncoder().encodeToString(data));
        channel.send(player, json.toString());
    }

    // ---------------------------------------------------------------- sounds

    /**
     * Registers an OGG sound under an id. The client stores it in a generated resource pack,
     * so {@link #playSound} can play it by id without any manual pack setup.
     */
    public void registerSound(String soundId, byte[] oggBytes) {
        sounds.put(soundId, oggBytes);
        soundVersions.merge(soundId, 1, Integer::sum);
    }

    /** Sends a registered sound to the player unless this client already received it. */
    public void preloadSound(Player player, String soundId) {
        sendSoundIfNeeded(player, soundId);
    }

    /** Plays a registered sound once on the player's client at the default volume. */
    public void playSound(Player player, String soundId) {
        playSound(player, soundId, 1.0f, 1.0f);
    }

    /** Plays a registered sound once on the player's client. */
    public void playSound(Player player, String soundId, float volume, float pitch) {
        sendSoundIfNeeded(player, soundId);
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_PLAY_SOUND);
        json.addProperty("soundId", soundId);
        json.addProperty("volume", volume);
        json.addProperty("pitch", pitch);
        json.addProperty("loop", false);
        channel.send(player, json.toString());
    }

    /** Plays a registered sound on repeat (background music, ambience) until {@link #stopSound}. */
    public void playLoopingSound(Player player, String soundId, float volume) {
        sendSoundIfNeeded(player, soundId);
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_PLAY_SOUND);
        json.addProperty("soundId", soundId);
        json.addProperty("volume", volume);
        json.addProperty("pitch", 1.0f);
        json.addProperty("loop", true);
        channel.send(player, json.toString());
    }

    /** 调整正在循环播放的音效的音量，不会从头重播（客户端 1.3.13+ 支持）。 */
    public void setLoopingSoundVolume(Player player, String soundId, float volume) {
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_SET_SOUND_VOLUME);
        json.addProperty("soundId", soundId);
        json.addProperty("volume", Math.max(0.0f, volume));
        channel.send(player, json.toString());
    }

    /** Stops one looping sound on the player's client; one-shot sounds are not affected. */
    public void stopSound(Player player, String soundId) {
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_STOP_SOUND);
        json.addProperty("soundId", soundId);
        channel.send(player, json.toString());
    }

    /** Stops every looping sound on the player's client. */
    public void stopAllSounds(Player player) {
        channel.send(player, Protocol.baseMessage(Protocol.TYPE_STOP_SOUND).toString());
    }

    /**
     * Makes the client play {@code soundId} for every widget click (buttons, images, panels).
     * Call once at plugin startup; {@code null} disables it again.
     */
    public void setClickSound(String soundId, float volume) {
        this.clickSound = soundId;
        this.clickSoundVolume = volume;
    }

    private void sendSoundIfNeeded(Player player, String soundId) {
        byte[] data = sounds.get(soundId);
        if (data == null) return;
        int version = soundVersions.getOrDefault(soundId, 1);
        Map<String, Integer> sent = sentSounds.computeIfAbsent(player.getUniqueId(),
                k -> new ConcurrentHashMap<>());
        Integer delivered = sent.get(soundId);
        if (delivered != null && delivered == version) return;
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_SOUND);
        json.addProperty("soundId", soundId);
        json.addProperty("data", java.util.Base64.getEncoder().encodeToString(data));
        channel.send(player, json.toString());
        sent.put(soundId, version);
    }

    // ---------------------------------------------------------------- input

    /**
     * Closes every GuiApi window of the player and then makes the client run a command,
     * exactly as if the player had typed it (leading {@code /} is optional).
     *
     * <p>Use this when the target feature lives in another plugin's client-side command.</p>
     */
    public void runCommand(Player player, String command) {
        closeWindow(player, null);
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_RUN_COMMAND);
        json.addProperty("command", command.startsWith("/") ? command.substring(1) : command);
        channel.send(player, json.toString());
    }

    /** Binds a server action to a client key id. The client never hardcodes keys, so this is required for shortcuts. */
    public void addKeyHandler(String keyId, Consumer<Player> handler) {
        keyHandlers.put(keyId, handler);
    }

    public void removeKeyHandler(String keyId) {
        keyHandlers.remove(keyId);
    }

    public void clearKeyHandlers() {
        keyHandlers.clear();
    }

    // ------------------------------------------------------- shortcut binding

    /** player -> (key id -> GLFW key code) */
    private final Map<UUID, Map<String, Integer>> keyBindings = new ConcurrentHashMap<>();

    /**
     * Binds a shortcut on the player's client. {@code keyCode} is a GLFW key code
     * (e.g. {@code org.lwjgl.glfw.GLFW.GLFW_KEY_K} = 75); {@code <= 0} removes the binding.
     * The binding is stored per player and re-sent automatically after every reconnect.
     */
    public void bindKey(Player player, String keyId, int keyCode) {
        if (keyCode <= 0) {
            unbindKey(player, keyId);
            return;
        }
        keyBindings.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(keyId, keyCode);
        sendKeyBinding(player, keyId, keyCode);
    }

    /** Removes one shortcut bound on the client. */
    public void unbindKey(Player player, String keyId) {
        Map<String, Integer> bindings = keyBindings.get(player.getUniqueId());
        if (bindings != null) bindings.remove(keyId);
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_UNBIND_KEY);
        json.addProperty("key", keyId);
        channel.send(player, json.toString());
    }

    /** Removes every shortcut the server bound for this player. */
    public void clearKeys(Player player) {
        keyBindings.remove(player.getUniqueId());
        channel.send(player, Protocol.baseMessage(Protocol.TYPE_CLEAR_KEYS).toString());
    }

    private void sendKeyBinding(Player player, String keyId, int keyCode) {
        JsonObject json = Protocol.baseMessage(Protocol.TYPE_BIND_KEY);
        json.addProperty("key", keyId);
        json.addProperty("code", keyCode);
        channel.send(player, json.toString());
    }

    /** @return true if the player's client has the GuiApi mod installed and handshook. */
    public boolean isClientSupported(Player player) {
        return supportedClients.containsKey(player.getUniqueId());
    }

    /**
     * 客户端上报的 GuiApi 模组版本（如 "1.3.12"）。
     *
     * @return 版本号；旧客户端没有上报版本时返回 null
     */
    public String clientVersion(Player player) {
        return clientVersions.get(player.getUniqueId());
    }

    // ---------------------------------------------------------------- internal dispatch

    void markReady(Player player, String modVersion) {
        supportedClients.put(player.getUniqueId(), System.currentTimeMillis());
        if (modVersion == null || modVersion.isBlank()) {
            // 旧客户端：拿不到版本号，插件应据此拒绝加入
            clientVersions.remove(player.getUniqueId());
        } else {
            clientVersions.put(player.getUniqueId(), modVersion.trim());
        }
        // a fresh handshake means a fresh client, so cached textures are gone
        sentImages.remove(player.getUniqueId());
        sentSounds.remove(player.getUniqueId());
        // the client loses server-bound shortcuts on every reconnect, so replay them here
        Map<String, Integer> bindings = keyBindings.get(player.getUniqueId());
        if (bindings != null) {
            bindings.forEach((keyId, keyCode) -> sendKeyBinding(player, keyId, keyCode));
        }
    }

    void handleKey(Player player, String keyId) {
        Consumer<Player> handler = keyHandlers.get(keyId);
        if (handler != null) handler.accept(player);
    }

    void handleEvent(Player player, String windowId, String widgetId, String action, com.google.gson.JsonElement value) {
        Map<String, GuiWindow> windows = openWindows.get(player.getUniqueId());
        GuiWindow window = windows == null ? null : windows.get(windowId);
        if (window == null) return;
        if ("close".equals(action)) {
            windows.remove(windowId);
            windowClosed(player, window);
            return;
        }
        if ("esc".equals(action)) {
            // the window refused a direct close (closeOnEsc = false), let the plugin decide
            if (window.escHandler() != null) {
                window.escHandler().accept(new GuiActionEvent(player, window, null, action, value));
            }
            return;
        }
        Widget widget = window.widget(widgetId);
        if (widget == null) return;
        GuiActionEvent event = new GuiActionEvent(player, window, widget, action, value);
        dispatch(window, widget, event);
    }

    private void dispatch(GuiWindow window, Widget widget, GuiActionEvent event) {
        if ("click".equals(event.action())) {
            String click = window.clickSoundSet() ? window.clickSound() : clickSound;
            float volume = window.clickSoundSet() ? window.clickSoundVolume() : clickSoundVolume;
            if (click != null) playSound(event.player(), click, volume, 1.0f);
        }
        if (widget instanceof com.guiapi.paper.widget.Button button && button.clickHandler() != null) {
            button.clickHandler().accept(event);
        } else if (widget instanceof com.guiapi.paper.widget.Slider slider && slider.changeHandler() != null) {
            slider.value(event.valueAsDouble());
            slider.changeHandler().accept(event);
        } else if (widget instanceof com.guiapi.paper.widget.Scrollbar scrollbar && scrollbar.changeHandler() != null) {
            scrollbar.value(event.valueAsDouble());
            scrollbar.changeHandler().accept(event);
        } else if (widget instanceof com.guiapi.paper.widget.Checkbox checkbox && checkbox.toggleHandler() != null) {
            checkbox.checked(event.valueAsBoolean());
            checkbox.toggleHandler().accept(event);
        } else if (widget instanceof com.guiapi.paper.widget.Radio radio && radio.toggleHandler() != null) {
            radio.toggleHandler().accept(event);
        } else if (widget instanceof com.guiapi.paper.widget.Dropdown dropdown && dropdown.selectHandler() != null) {
            dropdown.selected(event.valueAsInt());
            dropdown.selectHandler().accept(event);
        } else if (widget instanceof com.guiapi.paper.widget.TextField field) {
            field.text(event.valueAsString());
            if (field.changeHandler() != null) field.changeHandler().accept(event);
        } else if (widget instanceof com.guiapi.paper.widget.ItemWidget item && item.clickHandler() != null) {
            item.clickHandler().accept(event);
        } else if (widget instanceof com.guiapi.paper.widget.Panel panel && panel.clickHandler() != null) {
            panel.clickHandler().accept(event);
        } else if (widget instanceof ImageWidget image && image.clickHandler() != null) {
            image.clickHandler().accept(event);
        }
    }

    /** Called when a client closes a window (Esc / close button). Can be overridden by subclasses; default no-op hook below. */
    private void windowClosed(Player player, GuiWindow window) {
        Consumer<GuiWindow> hook = closeHooks.get(window.id());
        if (hook != null) hook.accept(window);
    }

    private final Map<String, Consumer<GuiWindow>> closeHooks = new ConcurrentHashMap<>();

    /** Optional hook fired when a player closes the given window on their client. */
    public void onWindowClosed(String windowId, Consumer<GuiWindow> hook) {
        closeHooks.put(windowId, hook);
    }

    void removePlayer(UUID playerId) {
        openWindows.remove(playerId);
        supportedClients.remove(playerId);
        clientVersions.remove(playerId);
        sentImages.remove(playerId);
        sentSounds.remove(playerId);
        keyBindings.remove(playerId);
        channel.discard(playerId);
    }

    /** Utility: window ids currently open for the player. */
    public Map<String, GuiWindow> windowsOf(Player player) {
        return openWindows.getOrDefault(player.getUniqueId(), Map.of());
    }
}
