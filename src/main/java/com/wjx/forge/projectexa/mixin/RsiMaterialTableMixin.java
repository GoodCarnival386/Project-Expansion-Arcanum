package com.wjx.forge.projectexa.mixin;

import com.huanghuang.rsintegration.crafting.tree.IngredientKey;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wjx.forge.projectexa.integrations.rsi.RsiEmcPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/** The workspace material table replaces the old PlanRenderEngine material area. */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.crafting.plan.PlanMaterialTable", remap = false)
public abstract class RsiMaterialTableMixin {
    @Shadow private ItemStack hoveredItem;
    @Unique private boolean projectexa$leftover;
    @Unique private boolean projectexa$hoveredLeftover;

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/huanghuang/rsintegration/crafting/plan/PlanMaterialTable$Row;key()"
                    + "Lcom/huanghuang/rsintegration/crafting/tree/IngredientKey;"))
    private IngredientKey projectexa$rememberRow(@Coerce Object row, Operation<IngredientKey> original) {
        projectexa$leftover = ((RsiMaterialTableRowAccessor) row).projectexa$leftover();
        return original.call(row);
    }

    @Redirect(method = "render", at = @At(value = "FIELD",
            target = "Lcom/huanghuang/rsintegration/crafting/plan/PlanMaterialTable;hoveredItem:Lnet/minecraft/world/item/ItemStack;",
            opcode = Opcodes.PUTFIELD))
    private void projectexa$rememberHoveredRow(@Coerce Object table, ItemStack stack) {
        hoveredItem = stack;
        projectexa$hoveredLeftover = !stack.isEmpty() && projectexa$leftover;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;", remap = true))
    private Component projectexa$annotateMaterial(ItemStack stack, Operation<Component> original) {
        Component name = original.call(stack);
        if (!projectexa$leftover && Minecraft.getInstance().screen instanceof RsiEmcPreview preview) {
            String suffix = preview.projectexa$emcSuffix(stack);
            if (!suffix.isEmpty()) return name.copy().append(suffix);
        }
        return name;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;"
                    + "Lnet/minecraft/world/item/ItemStack;II)V", remap = true))
    private void projectexa$showMaterialQuote(GuiGraphics graphics, Font font, ItemStack stack,
                                             int x, int y, Operation<Void> original) {
        if (!projectexa$hoveredLeftover && Minecraft.getInstance().screen instanceof RsiEmcPreview preview) {
            String suffix = preview.projectexa$emcSuffix(stack);
            if (!suffix.isEmpty()) {
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
                lines.add(Component.literal(suffix));
                graphics.renderTooltip(font, lines, stack.getTooltipImage(), stack, x, y);
                return;
            }
        }
        original.call(graphics, font, stack, x, y);
    }
}
