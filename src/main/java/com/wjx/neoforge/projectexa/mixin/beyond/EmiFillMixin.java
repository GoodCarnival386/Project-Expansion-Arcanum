package com.wjx.neoforge.projectexa.mixin.beyond;

import com.wintercogs.beyonddimensions.api.menu.workbench.StorageWorkbenchTypes;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.integration.module.emi.recipe.NetRecipeHandler;
import com.wjx.neoforge.projectexa.integrations.beyond.TerminalCrafting;
import com.wjx.neoforge.projectexa.integrations.beyond.client.TerminalClient;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(value = NetRecipeHandler.class, remap = false)
abstract class EmiFillMixin implements StandardRecipeHandler<DimensionsNetMenu> {
    @Shadow @Final private ResourceLocation workbench;
    @Shadow protected abstract int existingBatches(EmiRecipe recipe, DimensionsNetMenu menu);

    @Unique private ResourceLocation projectexa$id(EmiRecipe recipe, EmiCraftContext<DimensionsNetMenu> context) {
        var player = Minecraft.getInstance().player;
        if (!StorageWorkbenchTypes.CRAFTING.equals(workbench) || !TerminalCrafting.active(context.getScreenHandler(), player)) return null;
        var backing = recipe.getBackingRecipe();
        if (backing == null && recipe.getId() != null) backing = player.level().getRecipeManager().byKey(recipe.getId()).orElse(null);
        return backing != null && backing.value() instanceof CraftingRecipe craft && !craft.getIngredients().isEmpty() ? backing.id() : null;
    }

    @Unique private TerminalCrafting.Fill projectexa$plan(ResourceLocation id, EmiRecipe recipe, EmiCraftContext<DimensionsNetMenu> context) {
        int count = (int)Math.min(Integer.MAX_VALUE, (long)Math.max(1, context.getAmount()) + existingBatches(recipe, context.getScreenHandler()));
        return TerminalClient.preview(context.getScreenHandler(), id, count);
    }

    @Inject(method = "canCraft", at = @At("RETURN"), cancellable = true)
    private void projectexa$available(EmiRecipe recipe, EmiCraftContext<DimensionsNetMenu> ctx, CallbackInfoReturnable<Boolean> ci) {
        var id = projectexa$id(recipe, ctx);
        if (ci.getReturnValueZ() && id != null) ci.setReturnValue(TerminalClient.preview(ctx.getScreenHandler(), id, 1) != null);
    }

    @Inject(method = "craft", at = @At("HEAD"), cancellable = true)
    private void projectexa$fill(EmiRecipe recipe, EmiCraftContext<DimensionsNetMenu> ctx, CallbackInfoReturnable<Boolean> ci) {
        var id = projectexa$id(recipe, ctx);
        if (id == null) return;
        var plan = ctx.getAmount() > 0 ? projectexa$plan(id, recipe, ctx) : null;
        if (plan != null) {
            TerminalClient.submit(ctx.getScreenHandler(), plan);
            Minecraft.getInstance().setScreen(ctx.getScreen());
        }
        ci.setReturnValue(plan != null);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void projectexa$render(EmiRecipe recipe, EmiCraftContext<DimensionsNetMenu> ctx, List<Widget> widgets, GuiGraphics draw, CallbackInfo ci) {
        var id = projectexa$id(recipe, ctx);
        if (id != null && TerminalClient.preview(ctx.getScreenHandler(), id, 1) != null) ci.cancel();
    }

    @Override public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<DimensionsNetMenu> ctx) {
        var id = projectexa$id(recipe, ctx);
        if (id == null) return StandardRecipeHandler.super.getTooltip(recipe, ctx);
        var plan = projectexa$plan(id, recipe, ctx);
        var lines = plan == null ? TerminalClient.unavailable(ctx.getScreenHandler(), id) : plan.bill().tooltip();
        return lines.stream().map(line -> ClientTooltipComponent.create(line.getVisualOrderText())).toList();
    }
}
