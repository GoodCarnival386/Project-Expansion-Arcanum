package com.wjx.forge.projectexa.mixin;

import com.huanghuang.rsintegration.crafting.CraftingResolver;
import com.huanghuang.rsintegration.crafting.MaterialSourceSelection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wjx.forge.projectexa.integrations.rsi.ArcaneCraftingInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/** Keep RSI's native reservation, extraction and material-refund semantics. */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.crafting.ExtractionLedger", remap = false)
public abstract class RsiExtractionLedgerMixin {
    @Shadow @Final private Map<CraftingResolver.StackKey, Long> pendingInv;
    @Shadow @Final private MaterialSourceSelection materialSources;

    @Shadow
    private List<IItemHandler> selectedBackpackInventories(ServerPlayer player) {
        throw new AssertionError();
    }

    @Shadow
    private int countExactAvailableInInventory(ServerPlayer player, ItemStack template) {
        throw new AssertionError();
    }

    @Inject(method = "selectedBackpackInventories", at = @At("RETURN"), cancellable = true)
    private void projectexa$appendArcaneSource(ServerPlayer player,
            CallbackInfoReturnable<List<IItemHandler>> cir) {
        if (materialSources.backpacks()) {
            cir.setReturnValue(ArcaneCraftingInventory.append(player, materialSources.inventory(),
                    cir.getReturnValue(), Map.of()));
        }
    }

    @WrapOperation(method = {"findAvailableInInventory", "countExactAvailableInInventory",
            "countExactAvailableInInventoryLong"},
            at = @At(value = "INVOKE", target = "Lcom/huanghuang/rsintegration/crafting/ExtractionLedger;selectedBackpackInventories(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/List;"),
            remap = false)
    private List<IItemHandler> projectexa$budgetSelectedPendingEmc(@Coerce Object ledger, ServerPlayer player,
            Operation<List<IItemHandler>> original) {
        List<IItemHandler> sources = original.call(ledger, player);
        // Honor RSI's source selection: an excluded backpack source must stay excluded.
        // Rebudget only reservation scans, leaving actual extraction to the normal unreserved view.
        for (IItemHandler source : sources) {
            if (source instanceof ArcaneCraftingInventory) {
                return ArcaneCraftingInventory.append(player, materialSources.inventory(), sources, pendingInv);
            }
        }
        return sources;
    }

    @Inject(method = "findAvailableInInventory", at = @At("RETURN"), cancellable = true)
    private void projectexa$combinePhysicalAndArcaneStock(ServerPlayer player, Ingredient ingredient, int needed,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!materialSources.backpacks() || !cir.getReturnValue().isEmpty() || needed <= 0 || ingredient.isEmpty()) return;
        // RSI's generic scan subtracts pending per slot. A physical and a virtual slot
        // for the same key must instead share one pending counter, just like its exact path.
        ArcaneCraftingInventory virtual = new ArcaneCraftingInventory(player, materialSources.inventory(),
                selectedBackpackInventories(player), pendingInv);
        for (int slot = 0; slot < virtual.getSlots(); slot++) {
            ItemStack template = virtual.getStackInSlot(slot);
            if (template.isEmpty() || !ingredient.test(template)
                    || countExactAvailableInInventory(player, template) < needed) continue;
            pendingInv.merge(ArcaneCraftingInventory.key(template), (long) needed, Long::sum);
            cir.setReturnValue(template.copyWithCount(1));
            return;
        }
    }
}
