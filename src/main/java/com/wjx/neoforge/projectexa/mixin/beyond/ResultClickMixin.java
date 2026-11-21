package com.wjx.neoforge.projectexa.mixin.beyond;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wjx.neoforge.projectexa.integrations.beyond.TerminalCrafting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BDBaseMenu.class, remap = false)
abstract class ResultClickMixin {
    @WrapMethod(method = "clicked")
    private void projectexa$repeat(int slot, int button, ClickType type, Player actor, Operation<Void> original) {
        if (type != ClickType.PICKUP || (button != 0 && button != 1) || !(actor instanceof ServerPlayer player)
                || !((Object)this instanceof DimensionsNetMenu menu) || !TerminalCrafting.active(menu, player)
                || !menu.crafting().isResultSlot(slot) || menu.getSlot(slot).hasItem()) {
            original.call(slot, button, type, actor);
            return;
        }
        TerminalCrafting.duringTake(menu, player, () -> {
            var fill = TerminalCrafting.repeat(menu, player);
            var cursor = menu.getCarried();
            if (fill != null && (cursor.isEmpty() || ItemStack.isSameItemSameComponents(cursor, fill.output())
                    && cursor.getMaxStackSize() - cursor.getCount() >= fill.output().getCount()))
                TerminalCrafting.apply(menu, player, fill, fill.bill().total());
            return original.call(slot, button, type, actor);
        });
    }
}
