package com.guiapi.client.input;

import com.guiapi.client.net.ClientNet;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-registered, player-rebindable shortcuts.
 *
 * <p>Unlike {@link KeyBindings} (which polls GLFW directly and never shows up in the
 * Controls screen), these are vanilla {@link KeyMapping}s added at runtime via
 * {@link KeyBindingHelper}. Players can rebind them in Options &rarr; Controls and the
 * choice is persisted in {@code options.txt}.</p>
 *
 * <p>Presses are reported to the server with the logical {@code keyId} (same wire format
 * as {@code bind_key}), so the server only ever deals with ids, never physical codes.</p>
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
        int code = (defaultCode != null && defaultCode > 0) ? defaultCode : GLFW.GLFW_KEY_UNKNOWN;
        KeyMapping mapping = new KeyMapping(label, code, CATEGORY);
        KeyBindingHelper.registerKeyBinding(mapping);
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
        if (BY_ID.isEmpty() || mc.screen != null) return;
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
}
