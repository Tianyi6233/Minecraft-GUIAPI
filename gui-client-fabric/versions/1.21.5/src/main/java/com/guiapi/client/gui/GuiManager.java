package com.guiapi.client.gui;

import com.google.gson.JsonObject;
import com.guiapi.client.model.WindowModel;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the windows currently shown on the client and the images sent by the server.
 * One shared {@link GuiWindowScreen} renders all windows.
 */
public final class GuiManager {
    private GuiManager() {}

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("GuiApi");

    public static final List<GuiWindowScreen.WindowState> windows = new ArrayList<>();
    private static final Map<String, ResourceLocation> imageTextures = new HashMap<>();
    private static final Map<String, int[]> imageSizes = new HashMap<>();
    /** 原始 PNG：音效包装载时会重载资源，贴图会全部失效，需要按这些字节重新注册一次。 */
    private static final Map<String, byte[]> imageBytes = new HashMap<>();

    public static void handleMessage(JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "";
        Minecraft mc = Minecraft.getInstance();
        switch (type) {
            case "open_window" -> {
                WindowModel model = new WindowModel(json.getAsJsonObject("window"));
                GuiWindowScreen.WindowState existing = find(model.id);
                if (existing != null) {
                    existing.model = model; // replace content, keep dragged position
                    existing.focusedText = null;
                    existing.textCursor = 0;
                    // re-opening a window must raise it, otherwise a fullscreen page keeps hiding it
                    bringToFront(existing);
                } else {
                    windows.add(new GuiWindowScreen.WindowState(model));
                    normalizeOrder();
                }
                ensureScreen(mc);
            }
            case "close_window" -> {
                if (json.has("id")) {
                    GuiWindowScreen.WindowState ws = find(json.get("id").getAsString());
                    if (ws != null) windows.remove(ws);
                } else {
                    windows.clear();
                }
                if (windows.isEmpty() && mc.screen instanceof GuiWindowScreen) {
                    mc.setScreen(null);
                }
            }
            case "update_widget" -> {
                GuiWindowScreen.WindowState ws = find(opt(json, "window"));
                if (ws != null) {
                    com.guiapi.client.model.WidgetModel widget = ws.model.widget(opt(json, "widget"));
                    if (widget != null && json.has("props")) {
                        widget.merge(json.getAsJsonObject("props"));
                    }
                }
            }
            case "image" -> registerImage(opt(json, "imageId"), Base64.getDecoder().decode(opt(json, "data")));
            case "sound" -> GuiSound.register(opt(json, "soundId"), Base64.getDecoder().decode(opt(json, "data")));
            case "play_sound" -> GuiSound.play(opt(json, "soundId"),
                    json.has("volume") ? json.get("volume").getAsFloat() : 1.0f,
                    json.has("pitch") ? json.get("pitch").getAsFloat() : 1.0f,
                    json.has("loop") && json.get("loop").getAsBoolean());
            case "stop_sound" -> {
                if (json.has("soundId")) GuiSound.stop(json.get("soundId").getAsString());
                else GuiSound.stopAll();
            }
            case "set_sound_volume" -> GuiSound.setVolume(opt(json, "soundId"),
                    json.has("volume") ? json.get("volume").getAsFloat() : 1.0f);
            case "bind_key" -> {
                String key = opt(json, "key");
                int code = json.has("code") ? json.get("code").getAsInt() : -1;
                com.guiapi.client.input.KeyBindings.bind(key, code);
            }
            case "unbind_key" -> com.guiapi.client.input.KeyBindings.unbind(opt(json, "key"));
            case "clear_keys" -> com.guiapi.client.input.KeyBindings.clear();
            case "run_command" -> {
                windows.clear();
                if (mc.screen instanceof GuiWindowScreen) mc.setScreen(null);
                String command = opt(json, "command");
                if (!command.isEmpty() && mc.player != null && mc.getConnection() != null) {
                    // same path as typing the command in chat, so client-side plugins see it
                    mc.getConnection().sendCommand(command);
                }
            }
            default -> { }
        }
    }

    private static String opt(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : "";
    }

    private static GuiWindowScreen.WindowState find(String id) {
        for (GuiWindowScreen.WindowState ws : windows) if (ws.model.id.equals(id)) return ws;
        return null;
    }

    /** Moves a window up in the z-order, but never above an always-on-top window. */
    public static void bringToFront(GuiWindowScreen.WindowState target) {
        windows.remove(target);
        if (target.model.alwaysOnTop) {
            windows.add(target);
            return;
        }
        int insertAt = windows.size();
        for (int i = 0; i < windows.size(); i++) {
            if (windows.get(i).model.alwaysOnTop) {
                insertAt = i;
                break;
            }
        }
        windows.add(insertAt, target);
    }

    /** Stable-sorts the window list so always-on-top windows stay at the end (drawn last). */
    public static void normalizeOrder() {
        List<GuiWindowScreen.WindowState> pinned = new ArrayList<>();
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).model.alwaysOnTop) pinned.add(0, windows.remove(i));
        }
        windows.addAll(pinned);
    }

    private static void ensureScreen(Minecraft mc) {
        if (!(mc.screen instanceof GuiWindowScreen)) {
            mc.setScreen(new GuiWindowScreen());
        }
    }

    /** Called by the screen when the user closes everything (Esc). */
    public static void closeAllFromScreen() {
        for (GuiWindowScreen.WindowState ws : new ArrayList<>(windows)) {
            com.guiapi.client.net.ClientNet.sendClose(ws.model.id);
        }
        windows.clear();
    }

    public static void closeFromScreen(GuiWindowScreen.WindowState ws) {
        windows.remove(ws);
        com.guiapi.client.net.ClientNet.sendClose(ws.model.id);
        Minecraft mc = Minecraft.getInstance();
        if (windows.isEmpty() && mc.screen instanceof GuiWindowScreen) {
            mc.setScreen(null);
        }
    }

    // ------------------------------------------------------------ images

    private static void registerImage(String imageId, byte[] bytes) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("guiapi", "dynamic/" + sanitize(imageId));
            DynamicTexture texture = new DynamicTexture(() -> "guiapi_" + imageId, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            imageTextures.put(imageId, id);
            imageSizes.put(imageId, new int[]{image.getWidth(), image.getHeight()});
            imageBytes.put(imageId, bytes);
            LOG.info("GuiApi image '{}' ready: {}x{} ({} bytes)",
                    imageId, image.getWidth(), image.getHeight(), bytes.length);
        } catch (Exception e) {
            // Minecraft's NativeImage.read only decodes PNG; server plugins that send JPEG
            // would otherwise fail silently and just show an empty box.
            LOG.error("GuiApi image '{}' could not be decoded ({} bytes, PNG only)", imageId, bytes.length, e);
        }
    }

    private static String sanitize(String id) {
        return id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }

    public static ResourceLocation imageTexture(String imageId) {
        return imageTextures.get(imageId);
    }

    /** 资源包重载会清掉动态贴图，重载完成后按原始 PNG 重新注册。 */
    public static void reRegisterImages() {
        for (Map.Entry<String, byte[]> entry : new HashMap<>(imageBytes).entrySet()) {
            registerImage(entry.getKey(), entry.getValue());
        }
    }

    public static int[] imageSize(String imageId) {
        return imageSizes.get(imageId);
    }
}
