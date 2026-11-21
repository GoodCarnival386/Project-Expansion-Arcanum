package com.wjx.neoforge.projectexa.integrations.beyond;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wjx.neoforge.projectexa.util.ArcaneTabletAccess;
import com.wjx.neoforge.projectexa.util.Util;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EmcMaterials {
    private EmcMaterials() {}

    public static @Nullable IKnowledgeProvider account(Player player) {
        return player == null || player.isSpectator() || !ArcaneTabletAccess.hasTablet(player)
                ? null : Util.getKnowledgeProvider(player);
    }

    public static long price(@Nullable IKnowledgeProvider account, ItemStackKey key) {
        if (account == null) return 0;
        ItemStack stack = key.getReadOnlyStack();
        ItemStack clean = Util.cleanStack(stack);
        if (clean.isEmpty() || !ItemStack.isSameItemSameComponents(clean, stack) || !account.hasKnowledge(clean)) return 0;
        return Math.max(0, IEMCProxy.INSTANCE.getValue(clean));
    }

    public record Bill(Map<ItemStackKey, Long> items, BigInteger total) {
        public Bill { items = Map.copyOf(items); }
        public List<Component> tooltip() {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("projectexa.beyond.cost", total.toString()));
            items.entrySet().stream().sorted(java.util.Comparator.comparing(e -> e.getKey().getReadOnlyStack().getHoverName().getString()))
                    .forEach(e -> lines.add(Component.translatable("projectexa.beyond.buy", e.getValue(), e.getKey().getReadOnlyStack().getHoverName())));
            return lines;
        }
    }

    /** One balance shared by all exact, reproducible ingredient keys. */
    public static @Nullable Bill quote(IKnowledgeProvider account, Map<? extends IStackKey<?>, Long> missing) {
        BigInteger total = BigInteger.ZERO;
        var bought = new java.util.LinkedHashMap<ItemStackKey, Long>();
        for (var entry : missing.entrySet()) {
            if (!(entry.getKey() instanceof ItemStackKey item) || entry.getValue() < 1) return null;
            long unit = price(account, item);
            if (unit == 0) return null;
            total = total.add(BigInteger.valueOf(unit).multiply(BigInteger.valueOf(entry.getValue())));
            bought.put(item, entry.getValue());
        }
        return total.compareTo(account.getEmc()) <= 0 ? new Bill(bought, total) : null;
    }
}
