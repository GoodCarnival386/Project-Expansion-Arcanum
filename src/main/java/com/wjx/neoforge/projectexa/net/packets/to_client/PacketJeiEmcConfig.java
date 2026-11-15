package com.wjx.neoforge.projectexa.net.packets.to_client;

import com.wjx.neoforge.projectexa.Main;
import com.wjx.neoforge.projectexa.net.packets.IPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.wjx.neoforge.projectexa.config.JeiEmcClientState;

public record PacketJeiEmcConfig(boolean enabled, boolean worldPickEnabled) implements IPacket {
    public static final Type<PacketJeiEmcConfig> TYPE = new Type<>(Main.rl("jei_emc_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketJeiEmcConfig> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeBoolean(packet.enabled()); buf.writeBoolean(packet.worldPickEnabled()); },
            buf -> new PacketJeiEmcConfig(buf.readBoolean(), buf.readBoolean()));

    @Override public void handle(IPayloadContext context) {
        JeiEmcClientState.setEnabled(enabled);
        JeiEmcClientState.setWorldPickEnabled(worldPickEnabled);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
