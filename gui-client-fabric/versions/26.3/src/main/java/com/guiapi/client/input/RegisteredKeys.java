package com.guiapi.client.input;

import com.guiapi.client.net.ClientNet;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-registered, player-rebindable shortcuts (SDL / 26.x input layer).
 *
 * <p>Unlike {@link KeyBindings} (which polls SDL scancodes directly and never shows up
 * in the Controls screen), these are vanilla {@link KeyMapping}s added to
 * {@code KeyMapping.ALL} at runtime. Players can rebind them in Options &rarr; Controls
 * and the choice is persisted in {@code options.txt}.</p>
 *
 * <p>The wire protocol carries GLFW key codes for cross-version consistency; they are
 * translated to SDL scancodes before building the {@link InputConstants.Key}.</p>
 */
public final class RegisteredKeys {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("guiapi", "main"));
    private static final Map<String, KeyMapping> BY_ID = new HashMap<>();

    private RegisteredKeys() {}

    /** Registers (or no-ops if already registered) a rebindable shortcut. */
    public static void register(String keyId, String label, Integer defaultCode) {
        if (keyId == null || keyId.isEmpty() || label == null) return;
        if (BY_ID.containsKey(keyId)) return;
        int code = -1;
        if (defaultCode != null && defaultCode > 0) {
            int scancode = toScancode(defaultCode);
            if (scancode >= 0) code = scancode;
        }
        KeyMapping mapping = new KeyMapping(label, code, CATEGORY);
        allMappings().add(mapping);
        BY_ID.put(keyId, mapping);
    }

    /** Removes a previously registered shortcut from the Controls screen. */
    public static void unregister(String keyId) {
        KeyMapping mapping = BY_ID.remove(keyId);
        if (mapping != null) allMappings().remove(mapping);
    }

    /** Removes every registered shortcut (called on disconnect). */
    public static void clear() {
        List<KeyMapping> all = allMappings();
        for (KeyMapping mapping : BY_ID.values()) all.remove(mapping);
        BY_ID.clear();
    }

    /** Polls every registered mapping and reports presses to the server. */
    public static void tick(Minecraft mc) {
        if (BY_ID.isEmpty() || mc.gui.screen() != null) return;
        for (Map.Entry<String, KeyMapping> e : BY_ID.entrySet()) {
            KeyMapping m = e.getValue();
            while (m.consumeClick()) ClientNet.sendKey(e.getKey());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<KeyMapping> allMappings() {
        try {
            Field f = KeyMapping.class.getDeclaredField("ALL");
            f.setAccessible(true);
            return (List<KeyMapping>) f.get(null);
        } catch (ReflectiveOperationException e) {
            return java.util.Collections.emptyList();
        }
    }

    /** GLFW key code -> SDL scancode (mirrors KeyBindings.toScancode). */
    private static int toScancode(int glfw) {
        if (glfw >= 65 && glfw <= 90) return InputConstants.KEY_A + (glfw - 65);
        if (glfw >= 48 && glfw <= 57) return glfw == 48 ? InputConstants.KEY_0 : InputConstants.KEY_1 + (glfw - 49);
        if (glfw >= 290 && glfw <= 301) return InputConstants.KEY_F1 + (glfw - 290);
        if (glfw >= 302 && glfw <= 313) return InputConstants.KEY_F13 + (glfw - 302);
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
            case 330 -> 99;
            case 331 -> 84;
            case 332 -> InputConstants.KEY_MULTIPLY;
            case 333 -> 86;
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
            case 348 -> 101;
            default -> -1;
        };
    }
}
