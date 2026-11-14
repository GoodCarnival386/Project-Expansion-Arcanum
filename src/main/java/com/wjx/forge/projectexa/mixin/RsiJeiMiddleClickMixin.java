package com.wjx.forge.projectexa.mixin;

import com.huanghuang.rsintegration.mods.jei.JeiStoragePullPacket;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wjx.forge.projectexa.net.PacketHandler;
import com.wjx.forge.projectexa.config.JeiEmcClientState;
import com.wjx.forge.projectexa.net.packets.to_server.PacketJeiTransmute;
import com.wjx.forge.projectexa.net.packets.to_server.PacketOpenArcaneTransmutationTablet;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.Internal;
import mezz.jei.common.config.GiveMode;
import mezz.jei.common.config.IClientToggleState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.mods.jei.client.JeiCheatShortcuts", remap = false)
public abstract class RsiJeiMiddleClickMixin {
    @Unique private static final ThreadLocal<Boolean> projectexa$middle = ThreadLocal.withInitial(() -> false);

    @WrapMethod(method = "onMousePressed", remap = false)
    private static void projectexa$scopeMiddle(ScreenEvent.MouseButtonPressed.Pre event, Operation<Void> original) {
        boolean previous = projectexa$middle.get();
        projectexa$middle.set(event.getButton() == 2 && !Screen.hasControlDown());
        try { original.call(event); }
        finally {
            if (previous) projectexa$middle.set(true); else projectexa$middle.remove();
        }
    }

    @Redirect(method = "pullFromStorage", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V"), remap = false)
    private static void projectexa$pullFirst(SimpleChannel channel, Object message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!JeiEmcClientState.isEnabled() || !projectexa$middle.get() || !(message instanceof JeiStoragePullPacket pull)
                || minecraft.player == null || !PacketOpenArcaneTransmutationTablet.hasTablet(minecraft.player)
                || !(minecraft.screen instanceof AbstractContainerScreen<?>)) {
            channel.sendToServer(message);
            return;
        }
        IClientToggleState state = Internal.getClientToggleState();
        if (state.isCheatItemsEnabled()) {
            channel.sendToServer(message);
            return;
        }
        boolean matches;
        state.setCheatItemsEnabled(true);
        try {
            matches = Internal.getKeyMappings().getCheatItemStack().isActiveAndMatches(
                    InputConstants.Type.MOUSE.getOrCreate(2));
        } finally {
            state.setCheatItemsEnabled(false);
        }
        if (!matches) {
            channel.sendToServer(message);
            return;
        }
        int destination = Internal.getJeiClientConfigs().getClientConfig().getGiveMode() == GiveMode.MOUSE_PICKUP ? -1 : -2;
        boolean creative = destination == -1 && minecraft.screen instanceof CreativeModeInventoryScreen;
        ItemStack carried = creative ? minecraft.player.containerMenu.getCarried().copy() : ItemStack.EMPTY;
        PacketHandler.sendToServer(new PacketJeiTransmute(pull.stack().copyWithCount(1),
                minecraft.player.containerMenu.containerId, destination, false, creative, carried, true));
    }
}
