package com.wjx.forge.projectexa.mixin;

import com.huanghuang.rsintegration.crafting.plan.PlanResponse;
import com.wjx.forge.projectexa.integrations.rsi.RsiEmcPreview;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Layout hooks for RSI 1.5.3's card and tree views. Never applied to the workspace UI. */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.crafting.plan.CraftingPlanScreen", remap = false)
public abstract class RsiLegacyPlanLayoutMixin extends Screen implements RsiEmcPreview {
    @Shadow private PlanResponse plan;
    @Shadow private int contentLayoutWidth;
    @Shadow private int missingAreaHeight;

    protected RsiLegacyPlanLayoutMixin(Component title) { super(title); }

    @Override
    public void projectexa$invalidateLayout() {
        contentLayoutWidth = -1;
    }

    @Redirect(method = "layoutContentSections", at = @At(value = "FIELD",
            target = "Lcom/huanghuang/rsintegration/crafting/plan/CraftingPlanScreen;missingAreaHeight:I",
            opcode = Opcodes.PUTFIELD))
    private void projectexa$reserveQuoteHeader(@Coerce Object screen, int height) {
        // Pad the preceding section without changing native material scrolling or hitboxes.
        missingAreaHeight = height + (projectexa$emcTotal().isEmpty() ? 0 : font.lineHeight + 4);
    }

    @Inject(method = "stripRowCount", at = @At("RETURN"), cancellable = true)
    private void projectexa$reserveTreeHeader(CallbackInfoReturnable<Integer> cir) {
        if (!plan.materials().isEmpty() && !projectexa$emcTotal().isEmpty()) cir.setReturnValue(cir.getReturnValue() + 1);
    }

    @Inject(method = "renderStripRow", at = @At("HEAD"))
    private void projectexa$drawTreeQuote(GuiGraphics graphics, Font font, int centerX, int y, String label,
                                        int color, List<?> entries, int page, boolean leftover, int mouseX, int mouseY,
                                        CallbackInfo ci) {
        if (leftover || projectexa$emcTotal().isEmpty()) return;
        int width = font.width(label) + 8;
        for (Object entry : entries) {
            RsiStripEntryAccessor material = (RsiStripEntryAccessor) entry;
            String count = material.projectexa$available() + "/" + material.projectexa$count()
                    + projectexa$emcSuffix(material.projectexa$key().stack(1));
            width += 18 + font.width(count) + 6;
        }
        graphics.drawString(font, projectexa$emcTotal(), centerX - width / 2,
                y - font.lineHeight - 4, projectexa$emcColor());
    }
}
