package com.wjx.forge.projectexa.net.packets.to_client;

import com.wjx.forge.projectexa.config.JeiEmcClientState;
import com.wjx.forge.projectexa.net.packets.IPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record PacketJeiEmcConfig(boolean enabled, boolean worldPickEnabled) implements IPacket {
    @Override
    public void handle(NetworkEvent.Context context) {
        JeiEmcClientState.setEnabled(enabled);
        JeiEmcClientState.setWorldPickEnabled(worldPickEnabled);
    }

    @Override
    public void encode(FriendlyByteBuf buffer) { buffer.writeBoolean(enabled); buffer.writeBoolean(worldPickEnabled); }

    public static PacketJeiEmcConfig decode(FriendlyByteBuf buffer) {
        return new PacketJeiEmcConfig(buffer.readBoolean(), buffer.readBoolean());
    }
}
