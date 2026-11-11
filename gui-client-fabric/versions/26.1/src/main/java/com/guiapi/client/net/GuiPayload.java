package com.guiapi.client.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Raw byte payload for the guiapi:main plugin channel. */
public record GuiPayload(byte[] data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GuiPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("guiapi", "main"));

    public static final StreamCodec<FriendlyByteBuf, GuiPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.data),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new GuiPayload(bytes);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
