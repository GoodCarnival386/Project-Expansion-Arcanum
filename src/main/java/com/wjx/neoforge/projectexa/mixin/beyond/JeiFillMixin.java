package com.wjx.neoforge.projectexa.mixin.beyond;

import com.wintercogs.beyonddimensions.api.menu.workbench.StorageWorkbenchTypes;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.integration.module.jei.transfer.WorkbenchRecipeTransferHandler;
import com.wjx.neoforge.projectexa.integrations.beyond.TerminalCrafting;
import com.wjx.neoforge.projectexa.integrations.beyond.client.TerminalClient;
import com.wjx.neoforge.projectexa.integrations.beyond.client.TerminalJeiHint;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorkbenchRecipeTransferHandler.class, remap = false)
abstract class JeiFillMixin {
    @Shadow @Final private ResourceLocation workbench;

    @Inject(method = "transferRecipe(Lcom/wintercogs/beyonddimensions/common/menu/DimensionsNetMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"), cancellable = true)
    private void projectexa$fill(DimensionsNetMenu menu, Object recipe, IRecipeSlotsView slots, Player player,
                                  boolean maximum, boolean transfer, CallbackInfoReturnable<IRecipeTransferError> ci) {
        if (!StorageWorkbenchTypes.CRAFTING.equals(workbench) || !(recipe instanceof RecipeHolder<?> holder)
                || !(holder.value() instanceof CraftingRecipe craft) || craft.getIngredients().isEmpty()
                || !TerminalCrafting.active(menu, player) || !menu.menuSync().hasSnapshot()
                || player.level().getRecipeManager().byKey(holder.id()).isEmpty()) return;
        boolean bulk = maximum || (!transfer && Screen.hasShiftDown());
        var fill = TerminalClient.preview(menu, holder.id(), bulk ? Integer.MAX_VALUE : 1);
        if (fill == null) {
            ci.setReturnValue(new TerminalJeiHint(IRecipeTransferError.Type.USER_FACING, TerminalClient.unavailable(menu, holder.id())));
        } else if (transfer) {
            TerminalClient.submit(menu, fill);
            ci.setReturnValue(null);
        } else {
            ci.setReturnValue(new TerminalJeiHint(IRecipeTransferError.Type.COSMETIC, fill.bill().tooltip()));
        }
    }

}
