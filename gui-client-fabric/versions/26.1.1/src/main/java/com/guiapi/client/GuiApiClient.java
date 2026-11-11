package com.guiapi.client;

import com.guiapi.client.font.SysFont;
import com.guiapi.client.input.KeyBindings;
import com.guiapi.client.net.ClientNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Client entry point: networking and the ready handshake.
 *
 * <p>Keyboard shortcuts are not registered here — the server plugin binds them
 * at runtime through {@link KeyBindings}, so several plugins can each own a key.</p>
 */
public class GuiApiClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientNet.register();

        ClientTickEvents.END_CLIENT_TICK.register(KeyBindings::tick);
        // 启动后装一次系统字体包（让服务端可以用 guiapi:sysfont 指定 Windows 字体）
        ClientLifecycleEvents.CLIENT_STARTED.register(SysFont::ensure);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientNet.sendReady());
        // shortcuts belong to the server we just left, never carry them over
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            KeyBindings.clear();
            com.guiapi.client.gui.GuiSound.stopAll();
        });
    }
}
