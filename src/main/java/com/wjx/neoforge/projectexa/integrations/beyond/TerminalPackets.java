package com.wjx.neoforge.projectexa.integrations.beyond;

import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wjx.neoforge.projectexa.Main;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.math.BigInteger;

public final class TerminalPackets {
    private TerminalPackets() {}

    // Even 2^63 units at 2^63 EMC per unit fit comfortably in 32 bytes.
    public static final StreamCodec<io.netty.buffer.ByteBuf, BigInteger> COST =
            ByteBufCodecs.byteArray(32).map(bytes -> bytes.length == 0 ? BigInteger.ZERO : new BigInteger(1, bytes), BigInteger::toByteArray);

    public record Toggle(int menu, boolean enabled) implements CustomPacketPayload {
        static final Type<Toggle> TYPE = new Type<>(Main.rl("beyond_toggle"));
        @Override public Type<Toggle> type() { return TYPE; }
    }
    public record Fill(int menu, ResourceLocation recipe, int batches, BigInteger limit) implements CustomPacketPayload {
        static final Type<Fill> TYPE = new Type<>(Main.rl("beyond_fill"));
        @Override public Type<Fill> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Toggle.TYPE, StreamCodec.composite(ByteBufCodecs.VAR_INT, Toggle::menu,
                ByteBufCodecs.BOOL, Toggle::enabled, Toggle::new), (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof DimensionsNetMenu menu
                    && menu.containerId == msg.menu && menu.stillValid(player)) {
                var state = TerminalSession.of(menu);
                state.enabled = msg.enabled && EmcMaterials.account(player) != null;
                state.lastRecipe = null;
            }
        });
        StreamCodec<RegistryFriendlyByteBuf, Fill> fill = StreamCodec.composite(ByteBufCodecs.VAR_INT, Fill::menu,
                ResourceLocation.STREAM_CODEC, Fill::recipe, ByteBufCodecs.VAR_INT, Fill::batches, COST, Fill::limit, Fill::new);
        registrar.playToServer(Fill.TYPE, fill, (msg, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof DimensionsNetMenu menu)
                    || menu.containerId != msg.menu || !menu.stillValid(player)) return;
            var plan = TerminalCrafting.plan(menu, player, msg.recipe, msg.batches);
            if (plan == null || plan.batches() != msg.batches || !TerminalCrafting.apply(menu, player, plan, msg.limit))
                player.displayClientMessage(Component.translatable("projectexa.beyond.changed"), true);
        });
    }
}
