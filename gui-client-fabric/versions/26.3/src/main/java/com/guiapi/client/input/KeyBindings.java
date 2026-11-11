package com.guiapi.client.input;

import com.guiapi.client.net.ClientNet;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-controlled keyboard shortcuts.
 *
 * <p>The mod binds no key on its own: the Paper plugin decides which key opens which
 * window (see {@code GuiApi#bindKey}). Bindings arrive as {@code bind_key} /
 * {@code unbind_key} / {@code clear_keys} messages and are polled here every client tick,
 * so any number of plugins can own their own shortcut without colliding on a fixed key.</p>
 *
 * <p>Protocol v1 carries GLFW key codes on the wire; since 26.3 the client input layer
 * is SDL based, so codes are translated to SDL scancodes before polling.</p>
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

        // Never steal keys while a screen is open (chat, inventory, our own window, ...).
        boolean blocked = mc.gui.screen() != null;
        for (Map.Entry<Integer, String> entry : new HashMap<>(idByCode).entrySet()) {
            int code = entry.getKey();
            int scancode = toScancode(code);
            boolean down = scancode >= 0 && InputConstants.isKeyDown(scancode);
            if (!down) {
                held.remove(code);
            } else if (held.add(code) && !blocked) {
                ClientNet.sendKey(entry.getValue());
            }
        }
    }

    /**
     * GLFW key code -> SDL scancode. The wire protocol keeps GLFW codes (e.g. {@code GLFW_KEY_K}
     * = 75); the 26.3 input layer polls SDL scancodes instead.
     */
    private static int toScancode(int glfw) {
        if (glfw >= 65 && glfw <= 90) return InputConstants.KEY_A + (glfw - 65); // A..Z
        if (glfw >= 48 && glfw <= 57) return glfw == 48 ? InputConstants.KEY_0 : InputConstants.KEY_1 + (glfw - 49); // 0..9
        if (glfw >= 290 && glfw <= 301) return InputConstants.KEY_F1 + (glfw - 290); // F1..F12
        if (glfw >= 302 && glfw <= 313) return InputConstants.KEY_F13 + (glfw - 302); // F13..F24
        return switch (glfw) {
            case 32 -> InputConstants.KEY_SPACE;
            case 39 -> InputConstants.KEY_APOSTROPHE;
            case 44 -> InputConstants.KEY_COMMA;
            case 45 -> InputConstants.KEY_MINUS;
            case 46 -> InputConstants.KEY_PERIOD;
            case 47 -> InputConstants.KEY_SLASH;
            case 59 -> InputConstants.KEY_SEMICOLON;
            case 61 -> InputConstants.KEY_EQUALS;
            case 91 -> InputConstants.KEY_LBRACKET;
            case 92 -> InputConstants.KEY_BACKSLASH;
            case 93 -> InputConstants.KEY_RBRACKET;
            case 96 -> InputConstants.KEY_GRAVE;
            case 256 -> InputConstants.KEY_ESCAPE;
            case 257 -> InputConstants.KEY_RETURN;
            case 258 -> InputConstants.KEY_TAB;
            case 259 -> InputConstants.KEY_BACKSPACE;
            case 260 -> InputConstants.KEY_INSERT;
            case 261 -> InputConstants.KEY_DELETE;
            case 262 -> InputConstants.KEY_RIGHT;
            case 263 -> InputConstants.KEY_LEFT;
            case 264 -> InputConstants.KEY_DOWN;
            case 265 -> InputConstants.KEY_UP;
            case 266 -> InputConstants.KEY_PAGEUP;
            case 267 -> InputConstants.KEY_PAGEDOWN;
            case 268 -> InputConstants.KEY_HOME;
            case 269 -> InputConstants.KEY_END;
            case 280 -> InputConstants.KEY_CAPSLOCK;
            case 281 -> InputConstants.KEY_SCROLLLOCK;
            case 282 -> InputConstants.KEY_NUMLOCK;
            case 283 -> InputConstants.KEY_PRINTSCREEN;
            case 284 -> InputConstants.KEY_PAUSE;
            case 320 -> InputConstants.KEY_NUMPAD0;
            case 321 -> InputConstants.KEY_NUMPAD1;
            case 322 -> InputConstants.KEY_NUMPAD2;
            case 323 -> InputConstants.KEY_NUMPAD3;
            case 324 -> InputConstants.KEY_NUMPAD4;
            case 325 -> InputConstants.KEY_NUMPAD5;
            case 326 -> InputConstants.KEY_NUMPAD6;
            case 327 -> InputConstants.KEY_NUMPAD7;
            case 328 -> InputConstants.KEY_NUMPAD8;
            case 329 -> InputConstants.KEY_NUMPAD9;
            case 330 -> 99; // SDL scancode KP_PERIOD (no named constant)
            case 331 -> 84; // SDL scancode KP_DIVIDE
            case 332 -> InputConstants.KEY_MULTIPLY;
            case 333 -> 86; // SDL scancode KP_MINUS
            case 334 -> InputConstants.KEY_ADD;
            case 335 -> InputConstants.KEY_NUMPADENTER;
            case 336 -> InputConstants.KEY_NUMPADEQUALS;
            case 340 -> InputConstants.KEY_LSHIFT;
            case 341 -> InputConstants.KEY_LCONTROL;
            case 342 -> InputConstants.KEY_LALT;
            case 343 -> InputConstants.KEY_LGUI;
            case 344 -> InputConstants.KEY_RSHIFT;
            case 345 -> InputConstants.KEY_RCONTROL;
            case 346 -> InputConstants.KEY_RALT;
            case 347 -> InputConstants.KEY_RGUI;
            case 348 -> 101; // SDL scancode APPLICATION (menu key)
            default -> -1;
        };
    }
}
