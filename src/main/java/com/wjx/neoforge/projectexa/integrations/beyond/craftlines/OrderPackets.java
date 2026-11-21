package com.wjx.neoforge.projectexa.integrations.beyond.craftlines;

import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.network.SubmitOrderPayload;
import com.wjx.neoforge.projectexa.Main;
import com.wjx.neoforge.projectexa.integrations.beyond.TerminalPackets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

public final class OrderPackets {
    private OrderPackets() {}
    public record Approval(BigInteger maximum, Set<String> items) {}
    /** Approval travels with the decoded request through native validation, even when it schedules work. */
    public interface Approved {
        void projectexa$approve(Approval approval);
    }

    public record Submit(int menuId, BigInteger maximum, List<String> items, SubmitOrderPayload order) implements CustomPacketPayload {
        static final Type<Submit> TYPE = new Type<>(Main.rl("craftlines_submit"));
        @Override public Type<Submit> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar registrar) {
        StreamCodec<RegistryFriendlyByteBuf, Submit> codec = StreamCodec.composite(ByteBufCodecs.VAR_INT, Submit::menuId,
                TerminalPackets.COST, Submit::maximum,
                ByteBufCodecs.stringUtf8(512).apply(ByteBufCodecs.list(16384)), Submit::items,
                SubmitOrderPayload.STREAM_CODEC, Submit::order, Submit::new);
        registrar.playToServer(Submit.TYPE, codec, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof CraftlineOrderMenu menu)
                    || menu.containerId != payload.menuId || menu.dashboardConfiguration() || !menu.canAccessNetwork(player)) return;
            ((Approved)(Object)payload.order).projectexa$approve(new Approval(payload.maximum, Set.copyOf(payload.items)));
            SubmitOrderPayload.handle(payload.order, context);
        });
    }
}
