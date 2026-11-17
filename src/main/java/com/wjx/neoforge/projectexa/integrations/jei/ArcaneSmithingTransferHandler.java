package com.wjx.neoforge.projectexa.integrations.jei;

import com.wjx.neoforge.projectexa.gui.container.ContainerArcaneTransmutationTablet;
import com.wjx.neoforge.projectexa.net.packets.to_server.PacketArcaneSmithingTransfer;
import com.wjx.neoforge.projectexa.registries.MenuTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Smithing transfer is available only inside the arcane tablet's own menu. */
public final class ArcaneSmithingTransferHandler implements
        IRecipeTransferHandler<ContainerArcaneTransmutationTablet, RecipeHolder<SmithingRecipe>> {
    private static final IRecipeTransferError HIDDEN = () -> IRecipeTransferError.Type.INTERNAL;

    @Override public Class<ContainerArcaneTransmutationTablet> getContainerClass() {
        return ContainerArcaneTransmutationTablet.class;
    }

    @Override public Optional<MenuType<ContainerArcaneTransmutationTablet>> getMenuType() {
        return MenuTypes.ARCANE_TRANSMUTATION_TABLET.asOptional();
    }

    @Override public RecipeType<RecipeHolder<SmithingRecipe>> getRecipeType() { return RecipeTypes.SMITHING; }

    // Keep the legacy entry point as well as JEI's asynchronous transfer lifecycle.
    @SuppressWarnings("removal")
    @Override public @Nullable IRecipeTransferError transferRecipe(ContainerArcaneTransmutationTablet menu,
            RecipeHolder<SmithingRecipe> recipe, IRecipeSlotsView slots, Player player,
            boolean transferAll, boolean doTransfer) {
        return transfer(menu, recipe, slots, transferAll, doTransfer, -1);
    }

    @Override public @Nullable IRecipeTransferError transferRecipe(
            IRecipeTransferContext<RecipeHolder<SmithingRecipe>, ContainerArcaneTransmutationTablet> context,
            boolean doTransfer) {
        return JeiTransferCompletion.transfer(context, doTransfer, transferId -> transfer(
                context.getContainer(), context.getRecipe(), context.getRecipeSlots(),
                context.isMaxTransfer(), doTransfer, transferId));
    }

    private @Nullable IRecipeTransferError transfer(ContainerArcaneTransmutationTablet menu,
            RecipeHolder<SmithingRecipe> recipe, IRecipeSlotsView slots,
            boolean transferAll, boolean doTransfer, int transferId) {
        if (!menu.canUseStation(1) || slots.getSlotViews(RecipeIngredientRole.INPUT).size() != 3) return HIDDEN;
        var status = ArcaneSmithingTransferSupport.check(slots, menu, recipe.value());
        if (!doTransfer || !status.canTransfer(transferAll)) return status;
        PacketDistributor.sendToServer(new PacketArcaneSmithingTransfer(
                menu.containerId, recipe.id(), status.recipe(transferAll), transferAll, transferId));
        return null;
    }
}
