package com.wjx.forge.projectexa.net.packets.to_client;

import com.wjx.forge.projectexa.integrations.forgematica.ForgematicaMaterialListRefresh;
import com.wjx.forge.projectexa.net.packets.IPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

public record PacketForgematicaTransmuteResult(Component message) implements IPacket {
    @Override
    public void handle(NetworkEvent.Context context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
        if (minecraft.screen instanceof ForgematicaMaterialListRefresh materialList) {
            materialList.projectexa$refreshAvailableMaterials();
        }
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeComponent(message);
    }

    public static PacketForgematicaTransmuteResult decode(FriendlyByteBuf buffer) {
        return new PacketForgematicaTransmuteResult(buffer.readComponent());
    }
}
