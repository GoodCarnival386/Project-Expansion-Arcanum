package com.wjx.neoforge.projectexa.mixin.beyond;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.integration.module.emi.recipe.NetRecipeHandler;
import com.wjx.neoforge.projectexa.integrations.beyond.TerminalCrafting;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(value = RecipeFillButtonWidget.class, remap = false)
abstract class EmiButtonMixin {
    @Shadow private boolean canFill;
    @Shadow private List<ClientTooltipComponent> tooltip;
    @Unique private Runnable projectexa$refresh;

    // EMI caches a one-batch quote when constructing the button. Shift and the player's balance can change while it is open.
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ldev/emi/emi/api/recipe/handler/EmiRecipeHandler;getTooltip(Ldev/emi/emi/api/recipe/EmiRecipe;Ldev/emi/emi/api/recipe/handler/EmiCraftContext;)Ljava/util/List;"))
    private <T extends AbstractContainerMenu> List<ClientTooltipComponent> projectexa$track(EmiRecipeHandler<T> handler, EmiRecipe recipe,
            EmiCraftContext<T> context, Operation<List<ClientTooltipComponent>> original) {
        if (handler instanceof NetRecipeHandler<?> && context.getScreenHandler() instanceof DimensionsNetMenu menu
                && TerminalCrafting.active(menu, Minecraft.getInstance().player)) {
            projectexa$refresh = () -> {
                var current = new EmiCraftContext<>(context.getScreen(), context.getInventory(), context.getType(), context.getDestination(),
                        Screen.hasShiftDown() ? Integer.MAX_VALUE : 1);
                canFill = handler.canCraft(recipe, current);
                tooltip = handler.getTooltip(recipe, current);
            };
        }
        return original.call(handler, recipe, context);
    }

    @Inject(method = "getTooltip", at = @At("HEAD"))
    private void projectexa$hover(int x, int y, CallbackInfoReturnable<List<ClientTooltipComponent>> ci) {
        if (projectexa$refresh != null) projectexa$refresh.run();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void projectexa$click(int x, int y, int button, CallbackInfoReturnable<Boolean> ci) {
        if (projectexa$refresh != null) projectexa$refresh.run();
    }
}
