package com.guiapi.client.api;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 注册会显示在原版「选项 → 控制 → 按键绑定」里的快捷键。
 *
 * <p>与 {@code KeyBindings} 中由服务端下发的隐形快捷键不同，这里的快捷键由玩家自行查看、
 * 修改和解决冲突，设置会跟随原版 options.txt 持久化。注册发生在客户端模组初始化阶段，
 * 因此必须在 {@code onInitializeClient()} 期间调用。</p>
 */
public final class GuiApiKeybinds {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("guiapi", "main"));

    private static final Map<String, KeyMapping> BY_ID = new HashMap<>();
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private record Entry(KeyMapping mapping, Runnable action) {
    }

    private GuiApiKeybinds() {
    }

    /**
     * 注册一个仅用于展示和读取按键状态的快捷键，由调用方自行轮询
     * {@link KeyMapping#consumeClick()}。
     *
     * @param id         唯一标识，重复注册同一 id 会直接返回已有绑定
     * @param label      按键设置里显示的名称
     * @param defaultKey GLFW 键码；{@code null} 表示默认不绑定
     */
    public static synchronized KeyMapping register(String id, String label, Integer defaultKey) {
        return register(id, label, defaultKey, null);
    }

    /**
     * 注册一个会自动执行 {@code action} 的快捷键。动作只在没有打开任何界面时触发。
     *
     * @param defaultKey GLFW 键码；{@code null} 表示默认不绑定
     * @param action     按键触发时执行的动作，{@code null} 表示只注册不自动执行
     */
    public static synchronized KeyMapping register(String id, String label, Integer defaultKey, Runnable action) {
        KeyMapping existing = BY_ID.get(id);
        if (existing != null) return existing;
        KeyMapping mapping = new KeyMapping(label,
                defaultKey == null ? GLFW.GLFW_KEY_UNKNOWN : defaultKey, CATEGORY);
        KeyMappingHelper.registerKeyMapping(mapping);
        BY_ID.put(id, mapping);
        ENTRIES.add(new Entry(mapping, action));
        return mapping;
    }

    /** 返回已注册的快捷键，未注册时为 {@code null}。 */
    public static synchronized KeyMapping get(String id) {
        return BY_ID.get(id);
    }

    /** 由客户端 tick 调用，触发带动作的快捷键。 */
    public static void tick(Minecraft mc) {
        if (ENTRIES.isEmpty() || mc.screen != null) return;
        for (Entry entry : ENTRIES) {
            if (entry.action() == null) continue;
            while (entry.mapping().consumeClick()) entry.action().run();
        }
    }
}
