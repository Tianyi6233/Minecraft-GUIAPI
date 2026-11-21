package com.guiapi.client.input;

import com.guiapi.client.net.ClientNet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Server-registered, player-rebindable shortcuts.
 *
 * <p>Unlike {@link KeyBindings} (which polls GLFW directly and never shows up in the
 * Controls screen), these are vanilla {@link KeyMapping}s added to {@code KeyMapping.ALL}
 * and {@code Options.keyMappings} at runtime. Players can rebind them in Options &rarr;
 * Controls and the choice is persisted in {@code options.txt}.</p>
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
        allMappings().put(mapping.getName(), mapping);
        addToOptions(mapping);
        // The player may have rebound this shortcut on a previous session; the vanilla
        // Options loader only runs once at startup, so a KeyMapping created at runtime
        // would otherwise fall back to the default code. Read the saved value back.
        restoreSavedBinding(mapping);
        BY_ID.put(keyId, mapping);
    }

    /**
     * Reads the player's previously saved binding for this mapping from {@code options.txt}
     * and applies it. Falls back silently to the default code if nothing is saved or the
     * file cannot be read (e.g. read-only game directory).
     *
     * <p>{@code InputConstants} and its {@code getKey(String)} live in different packages
     * across MC versions, so the lookup is done reflectively.</p>
     */
    private static void restoreSavedBinding(KeyMapping mapping) {
        try {
            File optionsFile = new File(Minecraft.getInstance().gameDirectory, "options.txt");
            if (!optionsFile.exists()) return;
            Properties props = new Properties();
            // Minecraft writes options.txt as UTF-8; Java Properties defaults to ISO-8859-1,
            // which garbles non-ASCII key names (e.g. "key_打开主菜单") and makes the lookup miss.
            try (FileInputStream in = new FileInputStream(optionsFile)) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            String saved = props.getProperty("key_" + mapping.getName());
            if (saved == null || saved.isBlank()) return;
            Class<?> inputConstants = findInputConstantsClass();
            if (inputConstants == null) return;
            Object key = inputConstants.getMethod("getKey", String.class).invoke(null, saved);
            Method setKey = mapping.getClass().getMethod("setKey", key.getClass());
            setKey.invoke(mapping, key);
            // setKey() only updates the mapping's key field, which is what the Controls screen
            // displays. The actual key->KeyMapping lookup table used for input detection is a
            // static map that is only rebuilt by KeyMapping.resetMapping() (normally called by
            // the Controls screen). Without it, the old default key is still the one that fires.
            try {
                Method resetMapping = KeyMapping.class.getMethod("resetMapping");
                resetMapping.invoke(null);
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Exception e) {
            // ignore - fall back to the default binding
        }
    }

    /** Locates {@code InputConstants} across MC versions (package moved after 1.20.4). */
    private static Class<?> findInputConstantsClass() {
        String[] candidates = {
                "com.mojang.blaze3d.platform.InputConstants",
                "net.minecraft.client.player.InputConstants"
        };
        for (String name : candidates) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    /** Removes a previously registered shortcut from the Controls screen. */
    public static void unregister(String keyId) {
        KeyMapping mapping = BY_ID.remove(keyId);
        if (mapping != null) {
            allMappings().remove(mapping.getName());
            removeFromOptions(mapping);
        }
    }

    /** Removes every registered shortcut (called on disconnect). */
    public static void clear() {
        Map<String, KeyMapping> all = allMappings();
        for (KeyMapping mapping : BY_ID.values()) {
            all.remove(mapping.getName());
            removeFromOptions(mapping);
        }
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
    private static Map<String, KeyMapping> allMappings() {
        try {
            Field f = KeyMapping.class.getDeclaredField("ALL");
            f.setAccessible(true);
            return (Map<String, KeyMapping>) f.get(null);
        } catch (ReflectiveOperationException e) {
            return java.util.Collections.emptyMap();
        }
    }

    private static void addToOptions(KeyMapping mapping) {
        try {
            Options options = Minecraft.getInstance().options;
            Field f = Options.class.getField("keyMappings");
            f.setAccessible(true);
            KeyMapping[] old = (KeyMapping[]) f.get(options);
            KeyMapping[] newArr = Arrays.copyOf(old, old.length + 1);
            newArr[old.length] = mapping;
            f.set(options, newArr);
        } catch (Exception e) {
            // ignore
        }
    }

    private static void removeFromOptions(KeyMapping mapping) {
        try {
            Options options = Minecraft.getInstance().options;
            Field f = Options.class.getField("keyMappings");
            f.setAccessible(true);
            KeyMapping[] old = (KeyMapping[]) f.get(options);
            List<KeyMapping> list = new ArrayList<>(Arrays.asList(old));
            list.remove(mapping);
            f.set(options, list.toArray(new KeyMapping[0]));
        } catch (Exception e) {
            // ignore
        }
    }
}
