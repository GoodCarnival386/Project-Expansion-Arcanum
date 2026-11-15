package com.wjx.neoforge.projectexa.net.packets.to_server;

import com.wjx.neoforge.projectexa.Main;
import com.wjx.neoforge.projectexa.net.packets.IPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.wjx.neoforge.projectexa.net.JeiCreativeDelivery;
import net.minecraft.server.level.ServerPlayer;

public record PacketJeiCreativeCursorAck(java.util.UUID id, boolean accepted) implements IPacket {
    public static final Type<PacketJeiCreativeCursorAck> TYPE = new Type<>(Main.rl("jei_creative_cursor_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketJeiCreativeCursorAck> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeUUID(packet.id()); buf.writeBoolean(packet.accepted()); },
            buf -> new PacketJeiCreativeCursorAck(buf.readUUID(), buf.readBoolean()));

    @Override public void handle(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) JeiCreativeDelivery.finish(player, id, accepted);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
