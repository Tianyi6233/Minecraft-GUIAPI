package com.guiapi.client.input;

import com.guiapi.client.net.ClientNet;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-controlled keyboard shortcuts.
 *
 * <p>The mod binds no key on its own: the Paper plugin decides which GLFW key opens which
 * window (see {@code GuiApi#bindKey}). Bindings arrive as {@code bind_key} /
 * {@code unbind_key} / {@code clear_keys} messages and are polled here every client tick,
 * so any number of plugins can own their own shortcut without colliding on a fixed key.</p>
 */
public final class KeyBindings {
    /** GLFW key code -> key id reported to the server. */
    private static final Map<Integer, String> idByCode = new HashMap<>();
    /** Key codes currently held down, used for edge detection. */
    private static final Set<Integer> held = new HashSet<>();

    private KeyBindings() {}

    /** Binds (or rebinds) a key. {@code keyCode <= 0} removes the binding. */
    public static void bind(String keyId, int keyCode) {
        if (keyId == null || keyId.isEmpty()) return;
        idByCode.values().removeIf(keyId::equals);
        if (keyCode <= 0) return;
        held.remove(keyCode); // a key that was down before the rebind must not fire immediately
        idByCode.put(keyCode, keyId);
    }

    public static void unbind(String keyId) {
        if (keyId == null || keyId.isEmpty()) return;
        idByCode.values().removeIf(keyId::equals);
    }

    public static void clear() {
        idByCode.clear();
        held.clear();
    }

    /** Called every client tick: edge-detects bound keys and reports presses to the server. */
    public static void tick(Minecraft mc) {
        if (idByCode.isEmpty()) return;
        if (mc.player == null || mc.level == null || mc.getWindow() == null) return;
        long handle = mc.getWindow().getWindow();
        if (handle == 0L) return;

        // Never steal keys while a screen is open (chat, inventory, our own window, ...).
        boolean blocked = mc.screen != null;
        for (Map.Entry<Integer, String> entry : new HashMap<>(idByCode).entrySet()) {
            int code = entry.getKey();
            boolean down = GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
            if (!down) {
                held.remove(code);
            } else if (held.add(code) && !blocked) {
                ClientNet.sendKey(entry.getValue());
            }
        }
    }
}
