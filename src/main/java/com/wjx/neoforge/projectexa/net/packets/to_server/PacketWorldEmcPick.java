package com.wjx.neoforge.projectexa.net.packets.to_server;

import com.wjx.neoforge.projectexa.config.Config;
import com.wjx.neoforge.projectexa.Main;
import com.wjx.neoforge.projectexa.util.ArcaneTabletAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.wjx.neoforge.projectexa.net.packets.IPacket;
import com.wjx.neoforge.projectexa.util.Util;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.math.BigInteger;

public record PacketWorldEmcPick(BlockPos position, boolean stack) implements IPacket {
    public static final Type<PacketWorldEmcPick> TYPE = new Type<>(Main.rl("world_emc_pick"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketWorldEmcPick> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeBlockPos(packet.position()); buf.writeBoolean(packet.stack()); },
            buf -> new PacketWorldEmcPick(buf.readBlockPos(), buf.readBoolean()));

    @Override public void handle(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator() || player.containerMenu != player.inventoryMenu) return;
        HitResult trace = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (!(trace instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !position.equals(hit.getBlockPos())) return;
        ItemStack picked = player.level().getBlockState(position).getCloneItemStack(hit, player.level(), position, player);
        if (picked.isEmpty()) return;
        Inventory inventory = player.getInventory();
        ItemStack held = player.getMainHandItem();
        boolean topUp = stack && !held.isEmpty() && held.getCount() < held.getMaxStackSize()
                && ItemStack.isSameItemSameComponents(held, picked)
                && ItemStack.isSameItemSameComponents(held, Util.cleanStack(picked));
        if (!topUp && inventory.findSlotMatchingItem(picked) >= 0) return;
        if (!Config.server.enableWorldEmcPick.get() || !ArcaneTabletAccess.hasTablet(player)) return;
        ItemStack output = Util.cleanStack(picked);
        IKnowledgeProvider provider = Util.getKnowledgeProvider(player);
        if (output.isEmpty() || provider == null) { fail(player, "provider_unavailable"); return; }
        if (!provider.hasKnowledge(output)) return;
        long value = IEMCProxy.INSTANCE.getValue(output);
        if (value <= 0) { fail(player, "no_emc", output.getHoverName()); return; }
        BigInteger unit = BigInteger.valueOf(value);
        int requested = topUp ? held.getMaxStackSize() - held.getCount() : stack ? output.getMaxStackSize() : 1;
        int count = provider.getEmc().divide(unit).min(BigInteger.valueOf(requested)).intValue();
        if (count <= 0) { fail(player, "insufficient_emc", Long.toString(value)); return; }
        int target = topUp ? inventory.selected : inventory.getSuitableHotbarSlot();
        int empty = -1;
        if (topUp) {
            held.grow(count);
        } else {
            ItemStack displaced = inventory.getItem(target);
            if (!displaced.isEmpty()) {
                for (int i = 9; i < 36; i++) if (inventory.getItem(i).isEmpty()) { empty = i; break; }
                if (empty < 0) { fail(player, "no_space"); return; }
            }
            if (empty >= 0) inventory.setItem(empty, displaced);
            inventory.setItem(target, output.copyWithCount(count));
            inventory.selected = target;
        }
        inventory.setChanged();
        BigInteger cost = unit.multiply(BigInteger.valueOf(count));
        provider.setEmc(provider.getEmc().subtract(cost));
        provider.syncEmc(player);
        if (empty >= 0) player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, empty, inventory.getItem(empty)));
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, target, inventory.getItem(target)));
        player.connection.send(new ClientboundSetCarriedItemPacket(target));
        player.inventoryMenu.broadcastChanges();
        player.sendSystemMessage(Component.translatable("text.projectexa.jei_transmuted", count, output.getHoverName(), cost.toString()));
    }

    private static void fail(ServerPlayer player, String reason, Object... args) {
        player.sendSystemMessage(Component.translatable("text.projectexa.jei_transmute." + reason, args)
                .withStyle(ChatFormatting.RED));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
