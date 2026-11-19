package com.wjx.forge.projectexa.integrations.rsi;

import com.huanghuang.rsintegration.api.IntegrationResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

final class RsiItemIdentity {
    private RsiItemIdentity() {}

    static ItemStack toStack(IntegrationResourceKey key) {
        if (key.kind() != IntegrationResourceKey.ResourceKind.ITEM) return ItemStack.EMPTY;
        var item = ForgeRegistries.ITEMS.getValue(key.id());
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        var tag = key.identity();
        if (!tag.isEmpty()) stack.setTag(tag);
        return stack;
    }
}
