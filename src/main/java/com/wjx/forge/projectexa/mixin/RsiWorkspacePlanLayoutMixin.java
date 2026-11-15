package com.wjx.forge.projectexa.mixin;

import com.huanghuang.rsintegration.crafting.plan.PlanWorkspaceLayout;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wjx.forge.projectexa.integrations.rsi.RsiEmcPreview;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** RSI 1.6.0's workspace has no legacy card layout fields or strip-row renderer. */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.crafting.plan.CraftingPlanScreen", remap = false)
public abstract class RsiWorkspacePlanLayoutMixin extends Screen implements RsiEmcPreview {
    @Shadow private PlanWorkspaceLayout workspace;
    @Shadow private int stripMaxEntryWidth;
    @Shadow private List<?> cachedLeftoverStrip;
    @Shadow private ItemStack hoveredItemForTooltip;

    @Shadow
    private void rebuildMaterialStripCache() { throw new AssertionError(); }

    protected RsiWorkspacePlanLayoutMixin(Component title) { super(title); }

    @Override
    public void projectexa$invalidateLayout() {
        rebuildMaterialStripCache();
    }

    @ModifyExpressionValue(method = "layoutWorkspace", at = @At(value = "INVOKE",
            target = "Lcom/huanghuang/rsintegration/crafting/plan/PlanWorkspaceLayout;create(IIZI)"
                    + "Lcom/huanghuang/rsintegration/crafting/plan/PlanWorkspaceLayout;"))
    private PlanWorkspaceLayout projectexa$reserveWorkspaceQuote(PlanWorkspaceLayout layout) {
        if (projectexa$emcTotal().isEmpty()) return layout;
        int extra = font.lineHeight + 4;
        // Insert a line above the summary. Move the native layout and its hitboxes together,
        // keeping the footer fixed and taking the space out of the scrollable content.
        return new PlanWorkspaceLayout(layout.frame(), layout.header(),
                projectexa$move(layout.summary(), extra, false),
                projectexa$move(layout.toolbar(), extra, false),
                projectexa$move(layout.content(), extra, true), layout.footer(),
                projectexa$move(layout.tree(), extra, true),
                projectexa$move(layout.inspector(), extra, true),
                layout.confirm(), layout.cancel(), layout.compact());
    }

    @Unique
    private static PlanWorkspaceLayout.Rect projectexa$move(PlanWorkspaceLayout.Rect rect, int extra, boolean shrink) {
        return new PlanWorkspaceLayout.Rect(rect.x(), rect.y() + extra, rect.width(),
                shrink ? Math.max(1, rect.height() - extra) : rect.height());
    }

    @Inject(method = "renderWorkspaceSummary", at = @At("HEAD"))
    private void projectexa$drawWorkspaceQuote(GuiGraphics graphics, CallbackInfo ci) {
        String total = projectexa$emcTotal();
        if (total.isEmpty()) return;
        graphics.drawString(font, font.plainSubstrByWidth(total, Math.max(1, workspace.summary().width() - 16)),
                workspace.summary().x() + 8, workspace.summary().y() - font.lineHeight - 2, projectexa$emcColor());
    }

    @ModifyConstant(method = "renderWorkspaceSummary", constant = {@Constant(intValue = 70), @Constant(intValue = 80)})
    private int projectexa$fitMaterialQuote(int original) {
        if (projectexa$emcTotal().isEmpty()) return original;
        int width = workspace.summary().width();
        int leftovers = !cachedLeftoverStrip.isEmpty() && width >= 500 ? Math.min(140, width / 5) : 0;
        int available = Math.max(1, width - (workspace.compact() ? 45 : 90) - leftovers - 30);
        return Math.min(available, Math.max(original, stripMaxEntryWidth + 2));
    }

    // Compact summaries may still truncate a long quote. Keep its full text in the item tooltip.
    @WrapOperation(method = "renderDeferredTooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;"
                    + "Ljava/util/List;II)V", remap = true))
    private void projectexa$showFullMaterialQuote(GuiGraphics graphics, Font font, List<Component> lines,
                                                 int x, int y, Operation<Void> original) {
        String suffix = projectexa$emcSuffix(hoveredItemForTooltip);
        if (!suffix.isEmpty()) {
            lines = new ArrayList<>(lines);
            lines.add(Component.literal(suffix));
        }
        original.call(graphics, font, lines, x, y);
    }
}
