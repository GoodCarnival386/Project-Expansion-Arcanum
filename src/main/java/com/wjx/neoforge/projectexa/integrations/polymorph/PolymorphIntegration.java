package com.wjx.neoforge.projectexa.integrations.polymorph;

import com.illusivesoulworks.polymorph.api.PolymorphApi;
import com.wjx.neoforge.projectexa.gui.container.ContainerArcaneTransmutationTablet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class PolymorphIntegration {
    private PolymorphIntegration() {}

    public static Optional<RecipeHolder<CraftingRecipe>> getRecipe(ContainerArcaneTransmutationTablet menu,
                                                                  Level level, Player player, CraftingInput input) {
        return PolymorphApi.getInstance().getRecipeManager()
                .getPlayerRecipe(menu, RecipeType.CRAFTING, input, level, player);
    }

    public static void selectRecipe(Player player, RecipeHolder<CraftingRecipe> recipe) {
        var data = PolymorphApi.getInstance().getPlayerRecipeData(player);
        if (data != null) data.selectRecipe(recipe);
    }
}
