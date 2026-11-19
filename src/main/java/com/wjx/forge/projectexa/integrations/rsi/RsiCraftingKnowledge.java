package com.wjx.forge.projectexa.integrations.rsi;

import com.huanghuang.rsintegration.api.execution.RecursiveCraftSucceededEvent;
import com.wjx.forge.projectexa.config.Config;
import com.wjx.forge.projectexa.net.packets.to_server.PacketOpenArcaneTransmutationTablet;
import com.wjx.forge.projectexa.util.Util;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.event.PlayerAttemptLearnEvent;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.LinkedHashSet;
import java.util.Set;

public final class RsiCraftingKnowledge {
    private RsiCraftingKnowledge() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(RsiCraftingKnowledge::learnProducts);
    }

    private static void learnProducts(RecursiveCraftSucceededEvent event) {
        ServerPlayer player = event.player();
        if (!Config.rsiCraftingAutoLearn.get() || !PacketOpenArcaneTransmutationTablet.hasTablet(player)) return;
        IKnowledgeProvider provider = Util.getKnowledgeProvider(player);
        if (provider == null) return;
        Set<ItemInfo> visited = new LinkedHashSet<>();
        for (var product : event.producedItems().entrySet()) {
            if (product.getValue() <= 0) continue;
            ItemStack stack = RsiItemIdentity.toStack(product.getKey());
            if (stack.isEmpty()) continue;
            ItemStack cleanStack = Util.cleanStack(stack);
            if (cleanStack.isEmpty() || IEMCProxy.INSTANCE.getValue(cleanStack) <= 0) continue;
            ItemInfo raw = ItemInfo.fromStack(stack);
            ItemInfo clean = ItemInfo.fromStack(cleanStack);
            if (!visited.add(clean) || provider.hasKnowledge(clean)) continue;
            if (!MinecraftForge.EVENT_BUS.post(new PlayerAttemptLearnEvent(player, raw, clean))
                    && provider.addKnowledge(clean)) {
                provider.syncKnowledgeChange(player, clean, true);
            }
        }
    }
}
