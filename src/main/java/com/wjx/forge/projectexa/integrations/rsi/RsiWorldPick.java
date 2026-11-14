package com.wjx.forge.projectexa.integrations.rsi;

import com.wjx.forge.projectexa.mixin.RsiWorldPickInvoker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class RsiWorldPick {
    private RsiWorldPick() { }
    public static boolean tryPick(ServerPlayer player, ItemStack stack) {
        return !RsiWorldPickInvoker.projectexa$pick(player, stack, (byte) 5, false, null).isEmpty();
    }
}
