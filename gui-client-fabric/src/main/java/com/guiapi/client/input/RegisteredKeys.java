package com.guiapi.client.input;

import com.guiapi.client.net.ClientNet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
        // Restore the player's previously saved binding. We keep our own persistence file
        // (guiapi-keybindings.properties) because vanilla Options.save() deletes key_<label>
        // entries for KeyMappings that are no longer in Options.keyMappings — and we remove
        // ours on disconnect. Falling back to options.txt keeps compatibility with bindings
        // saved by older client versions that used options.txt directly.
        String saved = readSavedBinding(keyId);
        if (saved == null) saved = readFromOptionsTxt(label);
        if (saved != null) applyKey(mapping, saved);
        BY_ID.put(keyId, mapping);
    }

    /**
     * Reads a saved binding for the given keyId from our own persistence file
     * ({@code guiapi-keybindings.properties}).
     *
     * @return the saved key name (e.g. {@code "key.keyboard.m"}), or null if not present
     */
    private static String readSavedBinding(String keyId) {
        File file = keyBindingsFile();
        if (!file.exists()) return null;
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return null;
        }
        String v = props.getProperty(keyId);
        return (v == null || v.isBlank()) ? null : v;
    }

    /**
     * Reads the saved binding for the given label from vanilla {@code options.txt}.
     * Used as a fallback for bindings saved by older client versions.
     */
    private static String readFromOptionsTxt(String label) {
        try {
            File optionsFile = new File(Minecraft.getInstance().gameDirectory, "options.txt");
            if (!optionsFile.exists()) return null;
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(optionsFile)) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            String saved = props.getProperty("key_" + label);
            return (saved == null || saved.isBlank()) ? null : saved;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Applies a saved key name (e.g. {@code "key.keyboard.m"}) to the mapping and rebuilds
     * the static key lookup table so the new key actually fires.
     */
    private static void applyKey(KeyMapping mapping, String saved) {
        try {
            Class<?> inputConstants = findInputConstantsClass();
            if (inputConstants == null) return;
            Object key = inputConstants.getMethod("getKey", String.class).invoke(null, saved);
            Method setKey = mapping.getClass().getMethod("setKey", key.getClass());
            setKey.invoke(mapping, key);
            try {
                Method resetMapping = KeyMapping.class.getMethod("resetMapping");
                resetMapping.invoke(null);
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Persists all currently registered bindings to {@code guiapi-keybindings.properties}.
     * Called on disconnect so that bindings survive a full game restart (vanilla Options.save()
     * would otherwise drop the entries because we remove the KeyMappings from Options.keyMappings).
     */
    private static void saveBindings() {
        Properties props = new Properties();
        for (Map.Entry<String, KeyMapping> e : BY_ID.entrySet()) {
            String keyName = keyNameOf(e.getValue());
            if (keyName != null) props.setProperty(e.getKey(), keyName);
        }
        try (FileOutputStream out = new FileOutputStream(keyBindingsFile())) {
            props.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "GuiApi key bindings");
        } catch (Exception ignored) {
        }
    }

    private static String keyNameOf(KeyMapping mapping) {
        try {
            Method saveString = KeyMapping.class.getMethod("saveString");
            return (String) saveString.invoke(mapping);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static File keyBindingsFile() {
        return new File(Minecraft.getInstance().gameDirectory, "guiapi-keybindings.properties");
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
        // Persist current bindings before removing them, so they survive a full game restart
        // (vanilla Options.save() drops key_<label> entries for KeyMappings no longer in
        // Options.keyMappings, which we are about to remove).
        saveBindings();
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
