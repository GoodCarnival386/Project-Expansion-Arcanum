package com.wjx.neoforge.projectexa.net.packets.to_client;

import com.wjx.neoforge.projectexa.Main;
import com.wjx.neoforge.projectexa.net.packets.IPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.neoforged.neoforge.network.PacketDistributor;
import com.wjx.neoforge.projectexa.net.packets.to_server.PacketJeiCreativeCursorAck;

public record PacketJeiCreativeCursor(java.util.UUID id, ItemStack expected, ItemStack result) implements IPacket {
    public static final Type<PacketJeiCreativeCursor> TYPE = new Type<>(Main.rl("jei_creative_cursor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketJeiCreativeCursor> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUUID(packet.id());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.expected());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.result());
            }, buf -> new PacketJeiCreativeCursor(buf.readUUID(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

    @Override public void handle(IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean accepted = minecraft.player != null && minecraft.player.isCreative()
                && minecraft.screen instanceof CreativeModeInventoryScreen
                && ItemStack.matches(minecraft.player.containerMenu.getCarried(), expected);
        if (accepted) minecraft.player.containerMenu.setCarried(result.copy());
        PacketDistributor.sendToServer(new PacketJeiCreativeCursorAck(id, accepted));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
