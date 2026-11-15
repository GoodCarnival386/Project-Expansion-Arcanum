package com.wjx.neoforge.projectexa.net.packets.to_server;

import com.wjx.neoforge.projectexa.Main;
import com.wjx.neoforge.projectexa.net.packets.IPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.wjx.neoforge.projectexa.gui.container.ContainerArcaneTransmutationTablet;
import net.minecraft.server.level.ServerPlayer;

public record PacketArcaneAnvilName(String name) implements IPacket {
    public static final Type<PacketArcaneAnvilName> TYPE = new Type<>(Main.rl("arcane_anvil_name"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketArcaneAnvilName> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeUtf(packet.name(), 50),
            buf -> new PacketArcaneAnvilName(buf.readUtf(50)));

    @Override public void handle(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof ContainerArcaneTransmutationTablet menu
                && menu.getPlayer() == player && menu.stillValid(player) && menu.isStation(3)) {
            menu.getAnvil().setItemName(name);
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
