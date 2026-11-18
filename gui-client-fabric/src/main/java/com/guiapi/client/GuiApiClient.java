package com.guiapi.client;

import com.guiapi.client.api.GuiApiKeybinds;
import com.guiapi.client.font.SysFont;
import com.guiapi.client.gui.GuiManager;
import com.guiapi.client.gui.GuiSound;
import com.guiapi.client.input.KeyBindings;
import com.guiapi.client.net.ClientNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Client entry point: networking and the ready handshake.
 *
 * <p>Shortcuts come in two flavours: server plugins bind invisible keys at runtime
 * through {@link KeyBindings}, while other client mods register player-visible,
 * rebindable keys through {@link GuiApiKeybinds} during initialization.</p>
 */
public class GuiApiClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientNet.register();

        ClientTickEvents.END_CLIENT_TICK.register(KeyBindings::tick);
        ClientTickEvents.END_CLIENT_TICK.register(GuiApiKeybinds::tick);
        // 启动后装一次系统字体包（让服务端可以用 guiapi:sysfont 指定 Windows 字体）
        ClientLifecycleEvents.CLIENT_STARTED.register(SysFont::ensure);

        // A new connection must never inherit windows, images, shortcuts or packets.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            resetSession();
            ClientNet.sendReady();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetSession());
    }

    private static void resetSession() {
        KeyBindings.clear();
        GuiSound.stopAll();
        GuiManager.resetSession();
        ClientNet.resetSession();
    }
}
