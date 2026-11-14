package com.wjx.forge.projectexa.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.UUID;

@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.feature.sidepanel.RSSidePanelClickPacket", remap = false)
public interface RsiWorldPickInvoker {
    @Invoker(value = "handleSingleClick", remap = false)
    static ItemStack projectexa$pick(ServerPlayer player, ItemStack stack, byte action, boolean shift, UUID panel) {
        throw new AssertionError();
    }
}
