package com.wjx.forge.projectexa.net.packets.to_server;

import com.wjx.forge.projectexa.net.JeiCreativeDelivery;
import com.wjx.forge.projectexa.net.packets.IPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

public record PacketJeiCreativeCursorAck(UUID id, boolean accepted) implements IPacket {
    @Override
    public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null) JeiCreativeDelivery.finish(player, id, accepted);
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeBoolean(accepted);
    }

    public static PacketJeiCreativeCursorAck decode(FriendlyByteBuf buffer) {
        return new PacketJeiCreativeCursorAck(buffer.readUUID(), buffer.readBoolean());
    }
}
