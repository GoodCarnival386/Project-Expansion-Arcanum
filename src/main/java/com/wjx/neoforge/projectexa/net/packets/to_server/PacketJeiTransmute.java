package com.wjx.neoforge.projectexa.net.packets.to_server;

import com.wjx.neoforge.projectexa.Main;
import com.wjx.neoforge.projectexa.net.packets.IPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.wjx.neoforge.projectexa.config.Config;
import com.wjx.neoforge.projectexa.net.JeiCreativeDelivery;
import com.wjx.neoforge.projectexa.util.ArcaneTabletAccess;
import com.wjx.neoforge.projectexa.util.Util;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import java.math.BigInteger;

public record PacketJeiTransmute(ItemStack requested, int menuId, int destination, boolean single, boolean creativeCursor, ItemStack expectedCursor) implements IPacket {
    // -1: cursor, -2: inventory, 0..8: hotbar. Prices and capacity are resolved on the server.
    public static final Type<PacketJeiTransmute> TYPE = new Type<>(Main.rl("jei_transmute"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketJeiTransmute> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.requested());
                buf.writeVarInt(packet.menuId());
                buf.writeInt(packet.destination());
                buf.writeBoolean(packet.single());
                buf.writeBoolean(packet.creativeCursor());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.expectedCursor());
            }, buf -> new PacketJeiTransmute(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(),
                    buf.readInt(), buf.readBoolean(), buf.readBoolean(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

    @Override public void handle(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()
                || player.containerMenu.containerId != menuId || requested.isEmpty() || !player.containerMenu.stillValid(player)
                || destination < -2 || destination > 8) return;
        boolean useCreativeCursor = creativeCursor && destination == -1 && player.isCreative()
                && player.containerMenu == player.inventoryMenu;
        if (creativeCursor && !useCreativeCursor) return;
        if (!Config.server.enableJeiEmcRetrieval.get() || !ArcaneTabletAccess.hasTablet(player)) return;
        if (useCreativeCursor && JeiCreativeDelivery.hasPending(player)) {
            fail(player, "pending");
            return;
        }
        ItemStack output = Util.cleanStack(requested);
        IKnowledgeProvider provider = Util.getKnowledgeProvider(player);
        if (output.isEmpty()) {
            return;
        }
        if (provider == null) {
            fail(player, "provider_unavailable");
            return;
        }
        if (!provider.hasKnowledge(output)) {
            return;
        }
        long value = IEMCProxy.INSTANCE.getValue(output);
        if (value <= 0) {
            fail(player, "no_emc", output.getHoverName());
            return;
        }
        BigInteger cost = BigInteger.valueOf(value);
        int amount = provider.getEmc().divide(cost).min(BigInteger.valueOf(single ? 1 : output.getMaxStackSize())).intValue();
        if (amount <= 0) {
            fail(player, "insufficient_emc", cost.toString());
            return;
        }
        int delivered = 0;
        if (useCreativeCursor) {
            ItemStack existing = expectedCursor;
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, output)) {
                fail(player, "different_item");
                return;
            }
            int space = existing.isEmpty() ? output.getMaxStackSize() : existing.getMaxStackSize() - existing.getCount();
            delivered = Math.min(amount, Math.max(0, space));
            if (delivered == 0) {
                fail(player, "no_space");
                return;
            }
            ItemStack result = existing.isEmpty() ? output.copyWithCount(delivered) : existing.copy();
            if (!existing.isEmpty()) result.grow(delivered);
            JeiCreativeDelivery.begin(player, output.copyWithCount(delivered), existing, result);
        } else if (destination == -2) {
            ItemStack stack = output.copyWithCount(amount);
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(new PlayerMainInvWrapper(player.getInventory()), stack, false);
            delivered = amount - remaining.getCount();
        } else {
            ItemStack existing = destination == -1 ? player.containerMenu.getCarried() : player.getInventory().getItem(destination);
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, output)) {
                fail(player, "different_item");
                return;
            }
            int space = existing.isEmpty() ? output.getMaxStackSize() : existing.getMaxStackSize() - existing.getCount();
            delivered = Math.min(amount, Math.max(0, space));
            if (delivered == 0) {
                fail(player, "no_space");
                return;
            }
            if (existing.isEmpty()) {
                if (destination == -1) player.containerMenu.setCarried(output.copyWithCount(delivered));
                else player.getInventory().setItem(destination, output.copyWithCount(delivered));
            } else existing.grow(delivered);
        }
        if (delivered == 0) {
            fail(player, "no_space");
            return;
        }
        if (delivered > 0) {
            BigInteger totalCost = cost.multiply(BigInteger.valueOf(delivered));
            provider.setEmc(provider.getEmc().subtract(totalCost));
            provider.syncEmc(player);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.sendSystemMessage(Component.translatable("text.projectexa.jei_transmuted",
                    delivered, output.getHoverName(), totalCost.toString()));
        }
    }

    private static void fail(ServerPlayer player, String reason, Object... args) {
        player.sendSystemMessage(Component.translatable("text.projectexa.jei_transmute." + reason, args)
                .withStyle(ChatFormatting.RED));
    }


    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
