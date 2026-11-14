package com.wjx.forge.projectexa.net.packets.to_client;

import com.wjx.forge.projectexa.net.PacketHandler;
import com.wjx.forge.projectexa.net.packets.IPacket;
import com.wjx.forge.projectexa.net.packets.to_server.PacketJeiCreativeCursorAck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

public record PacketJeiCreativeCursor(UUID id, ItemStack expected, ItemStack result) implements IPacket {
    @Override
    public void handle(NetworkEvent.Context context) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean accepted = minecraft.player != null && minecraft.player.isCreative()
                && minecraft.screen instanceof CreativeModeInventoryScreen
                && ItemStack.matches(minecraft.player.containerMenu.getCarried(), expected);
        if (accepted) minecraft.player.containerMenu.setCarried(result.copy());
        PacketHandler.sendToServer(new PacketJeiCreativeCursorAck(id, accepted));
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeItem(expected);
        buffer.writeItem(result);
    }

    public static PacketJeiCreativeCursor decode(FriendlyByteBuf buffer) {
        return new PacketJeiCreativeCursor(buffer.readUUID(), buffer.readItem(), buffer.readItem());
    }
}
