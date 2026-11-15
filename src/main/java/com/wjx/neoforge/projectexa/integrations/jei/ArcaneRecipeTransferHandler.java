package com.wjx.neoforge.projectexa.integrations.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

/** Keeps the legacy entry point while completing modern transfers after a server acknowledgement. */
public abstract class ArcaneRecipeTransferHandler<C extends AbstractContainerMenu>
        implements IRecipeTransferHandler<C, RecipeHolder<CraftingRecipe>> {
    @SuppressWarnings("removal")
    @Override
    public @Nullable IRecipeTransferError transferRecipe(C container, RecipeHolder<CraftingRecipe> recipe,
                                                        IRecipeSlotsView slots, Player player,
                                                        boolean transferAll, boolean doTransfer) {
        return transferRecipe(container, recipe, slots, player, transferAll, doTransfer, -1);
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(IRecipeTransferContext<RecipeHolder<CraftingRecipe>, C> context,
                                                        boolean doTransfer) {
        return JeiTransferCompletion.transfer(context, doTransfer, transferId -> transferRecipe(
                context.getContainer(), context.getRecipe(), context.getRecipeSlots(), context.getPlayer(),
                context.isMaxTransfer(), doTransfer, transferId));
    }

    protected abstract @Nullable IRecipeTransferError transferRecipe(C container, RecipeHolder<CraftingRecipe> recipe,
                                                                    IRecipeSlotsView slots, Player player,
                                                                    boolean transferAll, boolean doTransfer, int transferId);
}
