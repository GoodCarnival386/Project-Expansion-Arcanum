package com.wjx.neoforge.projectexa.mixin.beyond;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wintercogs.beyonddimensions.api.menu.interaction.SlotClick;
import com.wintercogs.beyonddimensions.api.menu.workbench.StorageWorkbenchTypes;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.common.menu.interaction.BuiltinSlotInteractions;
import com.wintercogs.beyonddimensions.common.menu.widget.CraftingWidget;
import com.wjx.neoforge.projectexa.integrations.beyond.TerminalCrafting;
import com.wjx.neoforge.projectexa.integrations.beyond.TerminalSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;

@Mixin(value = BuiltinSlotInteractions.class, remap = false)
abstract class BatchCraftMixin {
    @Shadow private static ItemStack insertInto(SlotClick click, ItemStack stack, List<Integer> targets, boolean simulate) {
        throw new AssertionError();
    }

    @Shadow private static List<Integer> inventorySlots(SlotClick click) { throw new AssertionError(); }

    @WrapMethod(method = {"craftStack", "craftAll"})
    private static boolean projectexa$batch(SlotClick click, Operation<Boolean> original) {
        if (click.menu() instanceof DimensionsNetMenu menu && click.player() instanceof ServerPlayer player && TerminalCrafting.active(menu, player)
                && menu.crafting().isResultSlot(click.slot().index)) return TerminalCrafting.duringTake(menu, player, () -> original.call(click));
        return original.call(click);
    }

    @WrapOperation(method = "lambda$craft$33", at = @At(value = "INVOKE", target = "Lcom/wintercogs/beyonddimensions/common/menu/interaction/BuiltinSlotInteractions;putAway(Lcom/wintercogs/beyonddimensions/api/menu/interaction/SlotClick;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;)V"))
    private static void projectexa$deliver(SlotClick click, ItemStack crafted, List<Integer> targets, Operation<Void> original) {
        original.call(click, crafted, targets);
        if (click.menu() instanceof DimensionsNetMenu menu && click.player() instanceof ServerPlayer player)
            TerminalCrafting.returnDisplaced(menu, player);
    }

    @WrapOperation(method = {"craftStack", "craftAll"}, at = @At(value = "INVOKE", target = "Lcom/wintercogs/beyonddimensions/api/menu/interaction/SlotClick;clicked()Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;"))
    private static KeyAmount projectexa$firstCraft(SlotClick click, Operation<KeyAmount> original) {
        KeyAmount clicked = original.call(click);
        if (!clicked.isEmpty() || !(click.menu() instanceof DimensionsNetMenu menu) || !(click.player() instanceof ServerPlayer player)
                || !TerminalCrafting.active(menu, player) || !menu.crafting().isResultSlot(click.slot().index)) return clicked;
        var fill = TerminalCrafting.repeat(menu, player);
        if (fill == null) return clicked;
        var targets = new ArrayList<>(inventorySlots(click));
        if (!(click.space() && click.isLeft())) targets.addAll(menu.quickMoveRoutes().targets(click.slot().index));
        if (!insertInto(click, fill.output(), targets, true).isEmpty() || !TerminalCrafting.apply(menu, player, fill, fill.bill().total())) return clicked;
        ItemStack result = click.slot().getItem();
        return new KeyAmount(new ItemStackKey(result), result.getCount());
    }

    @Inject(method = "clearCraftInputs", at = @At("HEAD"))
    private static void projectexa$forgetClearedRecipe(SlotClick click, CallbackInfoReturnable<Boolean> ci) {
        if (click.menu() instanceof DimensionsNetMenu menu && menu.workbenchWidget(StorageWorkbenchTypes.CRAFTING) instanceof CraftingWidget grid
                && grid.inputSlots().contains(click.slot()))
            TerminalSession.of(menu).lastRecipe = null;
    }

    // This instruction is inside the bounded craft loop. There is no speculative refill after the last iteration.
    @WrapOperation(method = "lambda$craft$33", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack projectexa$nextCraft(Slot result, Operation<ItemStack> original, int times, Slot source,
                                                   ItemStack first, SlotClick click, List<Integer> targets) {
        ItemStack stack = original.call(result);
        if (!stack.isEmpty() || !(click.menu() instanceof DimensionsNetMenu menu) || !(click.player() instanceof ServerPlayer player)
                || !TerminalCrafting.active(menu, player) || !menu.crafting().isResultSlot(result.index)) return stack;
        var fill = TerminalCrafting.repeat(menu, player);
        if (fill == null || !ItemStack.matches(first, fill.output()) || !insertInto(click, fill.output().copy(), targets, true).isEmpty()) return stack;
        return TerminalCrafting.apply(menu, player, fill, fill.bill().total()) ? result.getItem() : stack;
    }
}
