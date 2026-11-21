package com.wjx.neoforge.projectexa.mixin.beyond.craftlines;

import com.amicbeam.beyondcraftlines.client.CraftlineOrderScreen;
import com.amicbeam.beyondcraftlines.common.crafting.*;
import com.amicbeam.beyondcraftlines.common.menu.CraftlineOrderMenu;
import com.amicbeam.beyondcraftlines.common.network.SubmitOrderPayload;
import com.amicbeam.beyondcraftlines.common.network.SubmissionFailurePayload;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wjx.neoforge.projectexa.integrations.beyond.EmcMaterials;
import com.wjx.neoforge.projectexa.integrations.beyond.craftlines.OrderPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import java.util.*;

@Mixin(value = CraftlineOrderScreen.class, remap = false)
abstract class OrderScreenMixin extends AbstractContainerScreen<CraftlineOrderMenu> {
    @Shadow protected abstract int treeRight();
    @Shadow protected abstract void rebuildTree();
    @Shadow protected abstract void markPreviewDirty();
    @Shadow private long previewNonce;
    @Shadow private long lastSubmittedNonce;
    @Shadow private long planningGeneration;
    @Shadow private boolean proposalReady;
    @Shadow private boolean planningSnapshotValid;
    @Shadow private Button orderButton;
    @Shadow @Final private Map<String, ResourceLocation> automaticResourceRecipes;
    @Shadow @Final private Map<IStackKey<?>, Long> extractionMaterials;

    @Unique private boolean projectexa$enabled;
    @Unique private final Set<String> projectexa$exchange = new LinkedHashSet<>();
    @Unique private EmcMaterials.Bill projectexa$quote;
    @Unique private Button projectexa$button;
    @Unique private OrderNodeAccess projectexa$hovered;

    protected OrderScreenMixin(CraftlineOrderMenu menu, Inventory inv, Component title) { super(menu, inv, title); }

    @Inject(method = "initDiagnostics", at = @At("HEAD"))
    private void projectexa$button(CallbackInfo ci) {
        if (menu.dashboardConfiguration() || !menu.initialError().isBlank()) return;
        projectexa$button = addRenderableWidget(Button.builder(Component.empty(), btn -> {
            if (!projectexa$enabled && EmcMaterials.account(minecraft.player) == null) {
                minecraft.player.displayClientMessage(Component.translatable("projectexa.beyond.tablet"), true);
                return;
            }
            projectexa$enabled = !projectexa$enabled;
            for (String token : projectexa$exchange) automaticResourceRecipes.remove(token);
            projectexa$quote = null;
            projectexa$updateButton();
            rebuildTree();
        }).bounds(treeRight() - 61, topPos + 5, 41, 18).build());
        projectexa$updateButton();
    }

    @Unique private void projectexa$updateButton() {
        if (projectexa$button == null) return;
        projectexa$button.setMessage(Component.literal(projectexa$enabled ? "EMC +" : "EMC −"));
        var tip = Component.translatable("projectexa.beyond.order_toggle");
        if (projectexa$enabled && projectexa$quote != null)
            projectexa$quote.tooltip().forEach(line -> tip.append("\n").append(line));
        projectexa$button.setTooltip(Tooltip.create(tip));
        if (orderButton != null) orderButton.setTooltip(projectexa$enabled ? Tooltip.create(tip) : null);
    }

    @Inject(method = "markPreviewDirty(IZ)V", at = @At("HEAD"))
    private void projectexa$clearOldQuote(int delay, boolean amountOnly, CallbackInfo ci) {
        if (projectexa$quote == null) return;
        projectexa$quote = null;
        projectexa$updateButton();
    }

    @WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lcom/amicbeam/beyondcraftlines/client/integration/jei/CraftlinesJeiPlugin;showRecipesFor(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;)Z"))
    private boolean projectexa$chooseSource(IStackKey<?> key, Operation<Boolean> original) {
        if (!projectexa$enabled || !Screen.hasAltDown() || !(key instanceof ItemStackKey item)
                || StackKeyMatch.exact(key, menu.initialTarget())) return original.call(key);
        String token = RecipeResourceResolver.resolutionKey(key);
        if (!projectexa$exchange.remove(token)) {
            if (menu.recipesForResourceOutput(key).isEmpty()) return original.call(key);
            if (EmcMaterials.price(EmcMaterials.account(minecraft.player), item) == 0) {
                minecraft.player.displayClientMessage(Component.translatable("projectexa.beyond.unavailable"), true);
                return true;
            }
            projectexa$exchange.add(token);
        }
        automaticResourceRecipes.remove(token);
        projectexa$quote = null;
        projectexa$updateButton();
        rebuildTree();
        return true;
    }

    @Inject(method = "selectedResourceRecipe", at = @At("HEAD"), cancellable = true)
    private void projectexa$stopBranch(IStackKey<?> key, RecipeHolder<?> fallback, CallbackInfoReturnable<RecipeHolder<?>> ci) {
        if (projectexa$enabled && projectexa$exchange.contains(RecipeResourceResolver.resolutionKey(key))) ci.setReturnValue(null);
    }

    @Inject(method = "genericRecipeOverrides", at = @At("RETURN"), cancellable = true)
    private void projectexa$pinSources(CallbackInfoReturnable<Map<String, ResourceLocation>> ci) {
        if (!projectexa$enabled || projectexa$exchange.isEmpty()) return;
        var fixed = new LinkedHashMap<>(ci.getReturnValue());
        projectexa$exchange.forEach(token -> fixed.put(token, RecipeResolutionOverrides.NO_RECIPE));
        ci.setReturnValue(Map.copyOf(fixed));
    }

    // Craftlines 0.7.3 passes a ResourceLocation to both variants of this tooltip.
    @ModifyArg(method = {"lambda$localizedRecipeType$54", "lambda$localizedRecipeType$55"}, index = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private static Object[] projectexa$recipeTypeText(Object[] arguments) {
        arguments[0] = arguments[0].toString();
        return arguments;
    }

    @Inject(method = "renderNodeTooltip", at = @At("HEAD"))
    private void projectexa$hover(GuiGraphics draw, @Coerce Object node, int x, int y, CallbackInfo ci) {
        projectexa$hovered = (OrderNodeAccess)node;
    }

    @ModifyArg(method = "renderNodeTooltip", index = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"))
    private List<Component> projectexa$leafHint(List<Component> lines) { return projectexa$nodeHint(lines); }

    @ModifyArg(method = "renderNodeTooltip", index = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;Lnet/minecraft/world/item/ItemStack;II)V"))
    private List<Component> projectexa$branchHint(List<Component> lines) { return projectexa$nodeHint(lines); }

    @Unique private List<Component> projectexa$nodeHint(List<Component> lines) {
        if (!projectexa$enabled || projectexa$hovered.projectexa$depth() == 0
                || !(projectexa$hovered.projectexa$key() instanceof ItemStackKey key)) return lines;
        boolean buying = projectexa$exchange.contains(RecipeResourceResolver.resolutionKey(key));
        var result = new ArrayList<>(lines);
        if (projectexa$quote != null) {
            var name = key.getReadOnlyStack().getHoverName();
            long stored = extractionMaterials.getOrDefault(key, 0L);
            long buy = projectexa$quote.items().getOrDefault(key, 0L);
            if (stored > 0) result.add(Component.translatable("projectexa.beyond.take", stored, name));
            if (buy > 0) result.add(Component.translatable("projectexa.beyond.buy", buy, name));
        }
        if (buying || (!menu.recipesForResourceOutput(key).isEmpty() && EmcMaterials.price(EmcMaterials.account(minecraft.player), key) > 0))
            result.add(Component.translatable(buying ? "projectexa.beyond.source_buy" : "projectexa.beyond.source_craft"));
        return result;
    }

    // The planner runs in the background. This callback accepts its result on the client thread, where knowledge is safe to read.
    @ModifyVariable(method = "lambda$startClientPlanning$71", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ClientRecipePlanner.Proposal projectexa$quote(ClientRecipePlanner.Proposal proposal,
            @Local(argsOnly = true, ordinal = 0) long generation, @Local(argsOnly = true, ordinal = 1) long nonce) {
        if (!projectexa$enabled || proposal == null || generation != planningGeneration || nonce != previewNonce) return proposal;
        projectexa$quote = null;
        var account = EmcMaterials.account(minecraft.player);
        if (account != null && (proposal.craftable() || proposal.outcome() == PlanningOutcome.MISSING_INPUTS)) {
            boolean eligible = proposal.missing().keySet().stream().allMatch(key -> {
                String token = RecipeResourceResolver.resolutionKey(key);
                ResourceLocation route = proposal.recipes().get(token);
                return !StackKeyMatch.exact(key, menu.initialTarget()) && (route == null
                        || RecipeResolutionOverrides.NO_RECIPE.equals(route)
                        && (projectexa$exchange.contains(token) || menu.recipesForResourceOutput(key).isEmpty()));
            });
            if (eligible) projectexa$quote = EmcMaterials.quote(account, proposal.missing());
        }
        projectexa$updateButton();
        if (projectexa$quote == null || proposal.craftable()) return proposal;
        // extraction() remains real stock, the EMC button lists the purchases separately.
        return new ClientRecipePlanner.Proposal(proposal.recipes(), proposal.ingredients(), Map.of(),
                proposal.extraction(), false, PlanningOutcome.READY);
    }

    @Inject(method = "startOptimalPlanning", at = @At("HEAD"), cancellable = true)
    private void projectexa$keepQuotedRoute(CallbackInfo ci) {
        if (projectexa$enabled) ci.cancel();
    }

    @Inject(method = "canQueueOrder", at = @At("HEAD"), cancellable = true)
    private void projectexa$awaitQuote(CallbackInfoReturnable<Boolean> ci) {
        if (projectexa$enabled && (!proposalReady || projectexa$quote == null)) ci.setReturnValue(false);
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void projectexa$requireQuote(CallbackInfo ci) {
        if (projectexa$enabled && (!proposalReady || projectexa$quote == null)) ci.cancel();
    }

    @Inject(method = "lambda$init$5", at = @At("TAIL"))
    private void projectexa$refreshPrice(SubmissionFailurePayload failure, CallbackInfo ci) {
        if (projectexa$enabled && failure.menuId() == menu.containerId && failure.nonce() == lastSubmittedNonce
                && (failure.error().endsWith(".planning_stale") || failure.error().endsWith(".planning_materials_changed"))) {
            planningSnapshotValid = false;
            markPreviewDirty();
        }
    }

    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/network/PacketDistributor;sendToServer(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
    private void projectexa$submit(CustomPacketPayload payload, CustomPacketPayload[] rest, Operation<Void> original) {
        if (!projectexa$enabled) { original.call(payload, rest); return; }
        if (projectexa$quote != null && payload instanceof SubmitOrderPayload order) {
            var items = projectexa$quote.items().keySet().stream().map(RecipeResourceResolver::resolutionKey).toList();
            PacketDistributor.sendToServer(new OrderPackets.Submit(menu.containerId, projectexa$quote.total(), items, order));
        } else {
            minecraft.player.displayClientMessage(Component.translatable("projectexa.beyond.changed"), true);
        }
    }
}
