package com.wjx.neoforge.projectexa.mixin.beyond.craftlines;

import com.amicbeam.beyondcraftlines.common.crafting.*;
import com.amicbeam.beyondcraftlines.common.network.SubmitOrderPayload;
import com.amicbeam.beyondcraftlines.common.runtime.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wjx.neoforge.projectexa.integrations.beyond.craftlines.OrderFunding;
import com.wjx.neoforge.projectexa.integrations.beyond.craftlines.OrderPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Set;
import java.util.UUID;

@Mixin(value = SubmitOrderPayload.class, remap = false)
abstract class SubmitOrderMixin implements OrderPackets.Approved {
    @Unique private OrderPackets.Approval projectexa$approval;
    @Override public void projectexa$approve(OrderPackets.Approval approval) { projectexa$approval = approval; }
    @WrapOperation(method = "lambda$handle$2", at = @At(value = "INVOKE", target = "Lcom/amicbeam/beyondcraftlines/common/crafting/RecipePlanningService;validateFixed(Lnet/minecraft/server/level/ServerLevel;Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;JLcom/amicbeam/beyondcraftlines/common/crafting/PlanningSnapshotService$Snapshot;Ljava/util/Set;Lcom/amicbeam/beyondcraftlines/common/crafting/RecipeResolutionOverrides;)Lcom/amicbeam/beyondcraftlines/common/crafting/RecipePlan;"))
    private static RecipePlan projectexa$verify(ServerLevel level, IStackKey<?> target, long count,
            PlanningSnapshotService.Snapshot stock, Set<String> families, RecipeResolutionOverrides choices, Operation<RecipePlan> original,
            @Local(argsOnly = true) IPayloadContext context, @Local(argsOnly = true) SubmitOrderPayload request,
            @Share("funding") LocalRef<OrderFunding> funding) {
        RecipePlan base = original.call(level, target, count, stock, families, choices);
        ServerPlayer player = (ServerPlayer)context.player();
        var approval = ((SubmitOrderMixin)(Object)request).projectexa$approval;
        if (approval == null) return base;
        var verified = OrderFunding.verify(player, base, stock, families, choices, approval);
        funding.set(verified);
        return verified.plan();
    }

    @WrapOperation(method = "lambda$handle$2", at = @At(value = "INVOKE", target = "Lcom/amicbeam/beyondcraftlines/common/runtime/RecipeOrderService;enqueueValidated(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;ILnet/minecraft/resources/ResourceLocation;JZLcom/amicbeam/beyondcraftlines/common/runtime/OrderOutputDestination;Lcom/amicbeam/beyondcraftlines/common/crafting/RecipePlan;)Lcom/amicbeam/beyondcraftlines/common/runtime/RecipeOrderJob;"))
    private static RecipeOrderJob projectexa$enqueue(ServerLevel level, UUID owner, int network, ResourceLocation id,
            long count, boolean blocking, OrderOutputDestination dest, RecipePlan plan, Operation<RecipeOrderJob> original,
            @Share("funding") LocalRef<OrderFunding> funding) {
        var payment = funding.get();
        if (payment == null) return original.call(level, owner, network, id, count, blocking, dest, plan);
        var job = payment.enqueue(() -> original.call(level, owner, network, id, count, blocking, dest, plan));
        payment.sync();
        return job;
    }
}
