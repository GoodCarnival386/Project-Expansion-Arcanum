package com.wjx.neoforge.projectexa.mixin.beyond;

import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AutoRefillResultSlot;
import com.wjx.neoforge.projectexa.integrations.beyond.TerminalCrafting;
import com.wjx.neoforge.projectexa.integrations.beyond.TerminalSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutoRefillResultSlot.class, remap = false)
abstract class ResultSlotMixin {
    @Shadow @Final private ResultContainer resultSlots;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void projectexa$rememberRecipe(Player player, ItemStack result, CallbackInfo ci) {
        if (player instanceof ServerPlayer && player.containerMenu instanceof DimensionsNetMenu menu && TerminalCrafting.active(menu, player)) {
            var recipe = resultSlots.getRecipeUsed();
            TerminalSession.of(menu).lastRecipe = recipe == null ? null : recipe.id();
        }
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void projectexa$rememberRemainders(Player player, ItemStack result, CallbackInfo ci) {
        if (player instanceof ServerPlayer && player.containerMenu instanceof DimensionsNetMenu menu && TerminalCrafting.active(menu, player))
            TerminalSession.of(menu).afterTake = menu.crafting().inputSlots().stream().map(s -> s.getItem().copy()).toList();
    }
}
