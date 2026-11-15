package com.wjx.neoforge.projectexa.net.packets.to_client;

import com.wjx.neoforge.projectexa.Main;
import com.wjx.neoforge.projectexa.net.packets.IPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketArcaneRecipeTransferResult(int transferId, int containerId, boolean success) implements IPacket {
    public static final Type<PacketArcaneRecipeTransferResult> TYPE = new Type<>(Main.rl("arcane_recipe_transfer_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketArcaneRecipeTransferResult> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PacketArcaneRecipeTransferResult::transferId,
            ByteBufCodecs.VAR_INT, PacketArcaneRecipeTransferResult::containerId,
            ByteBufCodecs.BOOL, PacketArcaneRecipeTransferResult::success,
            PacketArcaneRecipeTransferResult::new);

    @Override
    public void handle(IPayloadContext context) {
        if (ModList.get().isLoaded("jei")) {
            com.wjx.neoforge.projectexa.integrations.jei.JeiTransferCompletion.complete(transferId, containerId, success);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
