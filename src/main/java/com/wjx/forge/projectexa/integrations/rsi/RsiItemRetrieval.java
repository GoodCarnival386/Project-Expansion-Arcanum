package com.wjx.forge.projectexa.integrations.rsi;

import com.huanghuang.rsintegration.api.IntegrationResourceKey;
import com.huanghuang.rsintegration.api.RSIItemRetrievalFallback;
import com.huanghuang.rsintegration.api.RSIItemRetrievalFallback.Delivered;
import com.huanghuang.rsintegration.api.RSIItemRetrievalFallback.Destination;
import com.huanghuang.rsintegration.api.RSIItemRetrievalFallback.Request;
import com.huanghuang.rsintegration.api.RSIItemRetrievalFallback.Result;
import com.huanghuang.rsintegration.api.RSIItemRetrievalFallback.Scene;
import com.wjx.forge.projectexa.Main;
import com.wjx.forge.projectexa.config.Config;
import com.wjx.forge.projectexa.net.packets.to_server.PacketOpenArcaneTransmutationTablet;
import com.wjx.forge.projectexa.util.Util;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import java.math.BigInteger;

public final class RsiItemRetrieval {
    private RsiItemRetrieval() {}

    public static void register() {
        RSIItemRetrievalFallback.register(Main.rl("emc_retrieval"), RsiItemRetrieval::tryDeliver);
    }

    private static Result tryDeliver(Request request) {
        ServerPlayer player = request.player();
        Inventory inventory = player.getInventory();
        boolean worldPick = request.scene() == Scene.WORLD_PICK;
        if (worldPick) {
            if (!Config.enableWorldEmcPick.get() || request.destination() != Destination.SELECTED_HOTBAR_SLOT
                    || player.isCreative() || player.isSpectator() || player.containerMenu != player.inventoryMenu
                    || !inventory.getSelected().isEmpty()) return Result.PASS;
        } else if (request.scene() != Scene.JEI || !Config.enableJeiEmcRetrieval.get()
                || request.destination() != Destination.PLAYER_INVENTORY || player.isSpectator()) {
            return Result.PASS;
        }
        if (!PacketOpenArcaneTransmutationTablet.hasTablet(player)) return Result.PASS;

        ItemStack target = request.target();
        ItemStack output = Util.cleanStack(target);
        IKnowledgeProvider provider = Util.getKnowledgeProvider(player);
        if (output.isEmpty() || !target.areCapsCompatible(output)
                || !IntegrationResourceKey.item(target).equals(IntegrationResourceKey.item(output))
                || provider == null || !provider.hasKnowledge(output)) return Result.PASS;
        long value = IEMCProxy.INSTANCE.getValue(output);
        if (value <= 0) return Result.PASS;
        BigInteger balance = provider.getEmc();
        BigInteger price = BigInteger.valueOf(value);
        int limit = Math.min(request.requestedCount(), output.getMaxStackSize());
        int amount = balance.divide(price).min(BigInteger.valueOf(limit)).intValue();
        if (amount <= 0) return Result.PASS;

        ItemStack delivery = output.copyWithCount(amount);
        int delivered = amount;
        if (worldPick) {
            inventory.setItem(inventory.selected, delivery);
        } else {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(new PlayerMainInvWrapper(inventory), delivery, false);
            delivered -= remainder.getCount();
            if (delivered == 0) return Result.PASS;
        }

        BigInteger cost = price.multiply(BigInteger.valueOf(delivered));
        provider.setEmc(balance.subtract(cost));
        provider.syncEmc(player);
        player.sendSystemMessage(Component.translatable("text.projectexa.jei_transmuted",
                delivered, output.getHoverName(), cost.toString()));
        return new Delivered(delivered);
    }
}
