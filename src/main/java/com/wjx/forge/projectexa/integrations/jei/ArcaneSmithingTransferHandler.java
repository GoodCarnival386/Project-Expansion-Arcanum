package com.wjx.forge.projectexa.integrations.jei;

import com.wjx.forge.projectexa.net.PacketHandler;
import com.wjx.forge.projectexa.net.packets.to_server.PacketArcaneSmithingTransfer;
import com.wjx.forge.projectexa.net.packets.to_server.PacketOpenArcaneTransmutationTablet;
import com.wjx.forge.projectexa.util.Util;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SmithingRecipe;

import javax.annotation.Nullable;
import java.util.Optional;

public final class ArcaneSmithingTransferHandler implements IRecipeTransferHandler<AbstractContainerMenu, SmithingRecipe> {
    public static final ArcaneSmithingTransferHandler INSTANCE = new ArcaneSmithingTransferHandler();
    private static final IRecipeTransferError HIDDEN = () -> IRecipeTransferError.Type.INTERNAL;

    public static boolean available(Player player) {
        if (!PacketOpenArcaneTransmutationTablet.hasTablet(player)) return false;
        IKnowledgeProvider provider = Util.getKnowledgeProvider(player);
        return provider != null && provider.hasKnowledge(new ItemStack(Items.SMITHING_TABLE));
    }

    @Override public Class<? extends AbstractContainerMenu> getContainerClass() { return AbstractContainerMenu.class; }
    @Override public Optional<MenuType<AbstractContainerMenu>> getMenuType() { return Optional.empty(); }
    @Override public RecipeType<SmithingRecipe> getRecipeType() { return RecipeTypes.SMITHING; }

    @SuppressWarnings("removal")
    @Override public @Nullable IRecipeTransferError transferRecipe(AbstractContainerMenu container, SmithingRecipe recipe,
            IRecipeSlotsView slots, Player player, boolean transferAll, boolean doTransfer) {
        if (!available(player)) return HIDDEN;
        ArcaneCraftingTransferSupport.Status status = ArcaneCraftingTransferSupport.check(slots, player, container, true);
        if (!doTransfer || !status.canTransfer()) return status;
        PacketHandler.sendToServer(new PacketArcaneSmithingTransfer(recipe.getId(), status.recipe(transferAll), transferAll));
        return null;
    }
}
