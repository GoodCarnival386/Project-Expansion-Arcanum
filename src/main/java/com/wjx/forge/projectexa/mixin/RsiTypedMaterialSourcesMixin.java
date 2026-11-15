package com.wjx.forge.projectexa.mixin;

import com.huanghuang.rsintegration.crafting.CraftingResolver;
import com.huanghuang.rsintegration.crafting.MaterialSources;
import com.wjx.forge.projectexa.integrations.rsi.ArcaneCraftingInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** RSI 1.6.0 delegates inventory scans to this filtered entry point, including task admission. */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.crafting.MaterialSources", remap = false)
public abstract class RsiTypedMaterialSourcesMixin {
    @Redirect(method = "countInventoryForTypes", at = @At(value = "INVOKE",
            target = "Lcom/huanghuang/rsintegration/crafting/ExtractionLedger;findAllBackpackInventories(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/List;"))
    private static List<IItemHandler> projectexa$physicalBackpacksOnly(ServerPlayer player) {
        return RsiExtractionLedgerAccessor.projectexa$backpacks(player).stream()
                .filter(handler -> !(handler instanceof ArcaneCraftingInventory)).toList();
    }

    @Inject(method = "countInventoryForTypes", at = @At("RETURN"), cancellable = true)
    private static void projectexa$includeArcaneKnowledge(Player player, Set<Item> itemTypes,
            CallbackInfoReturnable<Map<CraftingResolver.StackKey, Long>> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Map<CraftingResolver.StackKey, Long> counts = new HashMap<>(cir.getReturnValue());
        ArcaneCraftingInventory virtual = new ArcaneCraftingInventory(serverPlayer,
                RsiExtractionLedgerAccessor.projectexa$backpacks(serverPlayer), Map.of());
        for (int slot = 0; slot < virtual.getSlots(); slot++) {
            ItemStack stack = virtual.getStackInSlot(slot);
            if (!stack.isEmpty() && (itemTypes == null || itemTypes.contains(stack.getItem()))) {
                counts.merge(ArcaneCraftingInventory.key(stack), (long) stack.getCount(), MaterialSources::saturatedAdd);
            }
        }
        cir.setReturnValue(counts);
    }
}
