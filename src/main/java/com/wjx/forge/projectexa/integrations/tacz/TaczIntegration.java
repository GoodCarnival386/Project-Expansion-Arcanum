package com.wjx.forge.projectexa.integrations.tacz;

import com.wjx.forge.projectexa.item.EmcWarehouseInventoryHandler;
import com.wjx.forge.projectexa.mixin.TaczGunSmithTableMenuAccessor;
import com.wjx.forge.projectexa.util.Util;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.api.item.IAmmo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.math.BigInteger;
import java.util.List;

/** TACZ integration. All inventory mutations are validated again on the server. */
public final class TaczIntegration {
    private static final int MAX_INPUTS = 64;
    public static final int MAX_HUD_AMMO_COUNT = 9_999;

    private TaczIntegration() {
    }

    /** TACZ stores the concrete ammunition id in NBT on a shared ammo item. */
    public static boolean isAmmo(ItemStack stack) {
        return stack.getItem() instanceof IAmmo;
    }

    public static int getWarehouseAmmoCount(Player player, ItemStack gunStack) {
        IItemHandler itemHandler = player.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (!(itemHandler instanceof EmcWarehouseInventoryHandler warehouseHandler)) {
            return 0;
        }
        return warehouseHandler.countMatchingWarehouseItems(available ->
                        available.getItem() instanceof IAmmo ammo
                                && ammo.isAmmoOfGun(gunStack, available),
                MAX_HUD_AMMO_COUNT);
    }

    public static void transmute(ServerPlayer player, ResourceLocation recipeId,
                                 List<ItemStack> displayedIngredients) {
        if (recipeId == null || displayedIngredients == null || displayedIngredients.size() > MAX_INPUTS
                || !(player.containerMenu instanceof GunSmithTableMenu menu) || !menu.stillValid(player)
                || player.level().getServer() == null) {
            return;
        }

        GunSmithTableRecipe recipe = ((TaczGunSmithTableMenuAccessor) (Object) menu)
                .projectexa$getRecipe(recipeId, player.level().getServer().getRecipeManager());
        if (recipe == null || recipe.getInputs().size() != displayedIngredients.size()) {
            return;
        }

        IKnowledgeProvider provider = Util.getKnowledgeProvider(player);
        if (provider == null) {
            return;
        }

        IItemHandler inventory = player.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (inventory == null) {
            return;
        }

        TransmutationInventory transmutation = new TransmutationInventory(player);
        for (int index = 0; index < recipe.getInputs().size(); index++) {
            GunSmithTableIngredient input = recipe.getInputs().get(index);
            ItemStack displayed = displayedIngredients.get(index);
            if (input.getCount() <= 0) {
                continue;
            }

            int missing = missingCount(input, inventory);
            if (missing <= 0) {
                continue;
            }

            BigInteger availableEmc = transmutation.getAvailableEmc();
            ItemStack item = findTransmutable(input.getIngredient(), displayed, provider, availableEmc);
            if (item.isEmpty()) {
                continue;
            }
            long value = IEMCProxy.INSTANCE.getValue(item);
            BigInteger unitCost = BigInteger.valueOf(value);
            int affordable = Util.safeIntValue(availableEmc.divide(unitCost));
            int count = Math.min(missing, affordable);
            if (count <= 0) {
                continue;
            }

            transmutation.removeEmc(unitCost.multiply(BigInteger.valueOf(count)));
            Util.returnToInventory(player.getInventory(), player, item.copyWithCount(count), true);
        }
    }

    private static int missingCount(GunSmithTableIngredient input, IItemHandler inventory) {
        int missing = input.getCount();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && input.getIngredient().test(stack)) {
                missing -= Math.min(missing, stack.getCount());
                if (missing == 0) {
                    break;
                }
            }
        }
        return missing;
    }

    private static ItemStack findTransmutable(Ingredient ingredient, ItemStack displayed,
                                             IKnowledgeProvider provider, BigInteger availableEmc) {
        ItemStack preferred = transmutableOption(ingredient, displayed, provider, availableEmc);
        if (!preferred.isEmpty()) {
            return preferred;
        }
        for (ItemStack option : ingredient.getItems()) {
            ItemStack candidate = transmutableOption(ingredient, option, provider, availableEmc);
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack transmutableOption(Ingredient ingredient, ItemStack option,
                                                IKnowledgeProvider provider, BigInteger availableEmc) {
        if (option == null || option.isEmpty() || !ingredient.test(option)) {
            return ItemStack.EMPTY;
        }
        ItemStack clean = Util.cleanStack(option);
        if (clean.isEmpty() || !ingredient.test(clean) || !provider.hasKnowledge(clean)) {
            return ItemStack.EMPTY;
        }
        long value = IEMCProxy.INSTANCE.getValue(clean);
        return value > 0 && availableEmc.compareTo(BigInteger.valueOf(value)) >= 0 ? clean : ItemStack.EMPTY;
    }
}
