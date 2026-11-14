package com.wjx.forge.projectexa.integrations.rsi;

import com.huanghuang.rsintegration.mods.jei.JeiStoragePullPacket;
import com.wjx.forge.projectexa.mixin.RsiJeiStoragePullInvoker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Captures RSI's own failure notification while its normal pull handler runs. */
public final class RsiJeiPullCoordinator {
    private static final ThreadLocal<Capture> ACTIVE = new ThreadLocal<>();
    private static final class Capture { Component failure; }
    private RsiJeiPullCoordinator() { }

    public static Component tryPull(ServerPlayer player, ItemStack stack) {
        Capture previous = ACTIVE.get();
        Capture capture = new Capture();
        ACTIVE.set(capture);
        try {
            RsiJeiStoragePullInvoker.projectexa$handleOnServer(new JeiStoragePullPacket(stack), player);
            return capture.failure;
        } finally {
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }

    public static boolean captureFailure(String key, Object[] args) {
        Capture capture = ACTIVE.get();
        if (capture == null) return false;
        capture.failure = Component.translatable(key, args);
        return true;
    }
}
