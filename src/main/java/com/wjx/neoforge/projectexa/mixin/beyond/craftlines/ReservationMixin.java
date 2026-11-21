package com.wjx.neoforge.projectexa.mixin.beyond.craftlines;

import com.amicbeam.beyondcraftlines.common.crafting.RecipePlan;
import com.amicbeam.beyondcraftlines.common.runtime.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wjx.neoforge.projectexa.integrations.beyond.craftlines.OrderFunding;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(value = RecipeOrderService.class, remap = false)
abstract class ReservationMixin {
    @Shadow private static void releaseReservations(UnifiedStorage storage, List<RecipePlan.ReservedMaterial> reserved) { throw new AssertionError(); }

    @WrapOperation(method = "enqueueValidated(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;ILnet/minecraft/resources/ResourceLocation;JZLcom/amicbeam/beyondcraftlines/common/runtime/OrderOutputDestination;Lcom/amicbeam/beyondcraftlines/common/runtime/OrderOrigin;Lcom/amicbeam/beyondcraftlines/common/crafting/RecipePlan;)Lcom/amicbeam/beyondcraftlines/common/runtime/RecipeOrderJob;", at = @At(value = "INVOKE", target = "Lcom/amicbeam/beyondcraftlines/common/runtime/RecipeOrderService;reserveInitial(Lcom/wintercogs/beyonddimensions/api/dimensionnet/UnifiedStorage;Ljava/util/List;)Ljava/util/List;"))
    private static List<RecipePlan.ReservedMaterial> projectexa$reserve(UnifiedStorage storage, List<RecipePlan.ReservedMaterial> required,
            Operation<List<RecipePlan.ReservedMaterial>> reserve, @Local(argsOnly = true) RecipePlan plan) {
        var payment = OrderFunding.current();
        if (payment == null || payment.plan() != plan) return reserve.call(storage, required);
        var taken = new ArrayList<RecipePlan.ReservedMaterial>(required.size());
        try {
            for (var material : required) taken.addAll(reserve.call(storage, List.of(material)));
            return List.copyOf(taken);
        } catch (RuntimeException | Error failure) {
            releaseReservations(storage, taken);
            throw failure;
        }
    }

    // Craftlines constructs the job outside its save rollback. Return only real stock if construction fails.
    @WrapOperation(method = "enqueueValidated(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;ILnet/minecraft/resources/ResourceLocation;JZLcom/amicbeam/beyondcraftlines/common/runtime/OrderOutputDestination;Lcom/amicbeam/beyondcraftlines/common/runtime/OrderOrigin;Lcom/amicbeam/beyondcraftlines/common/crafting/RecipePlan;)Lcom/amicbeam/beyondcraftlines/common/runtime/RecipeOrderJob;", at = @At(value = "INVOKE", target = "Lcom/amicbeam/beyondcraftlines/common/runtime/RecipeOrderJob;create(Ljava/util/UUID;Ljava/util/UUID;ILnet/minecraft/resources/ResourceLocation;JLjava/util/List;ZLcom/amicbeam/beyondcraftlines/common/runtime/OrderOutputDestination;Lcom/amicbeam/beyondcraftlines/common/runtime/RecipeOrderJob$Status;Ljava/lang/String;JJLjava/util/List;)Lcom/amicbeam/beyondcraftlines/common/runtime/RecipeOrderJob;"))
    private static RecipeOrderJob projectexa$materials(UUID id, UUID owner, int networkId, ResourceLocation target, long count,
            List<RecipePlan.Step> steps, boolean blocking, OrderOutputDestination destination, RecipeOrderJob.Status status,
            String message, long created, long finished, List<RecipePlan.ReservedMaterial> real, Operation<RecipeOrderJob> create,
            @Local(argsOnly = true) RecipePlan plan, @Local DimensionsNet network) {
        var payment = OrderFunding.current();
        if (payment == null || payment.plan() != plan)
            return create.call(id, owner, networkId, target, count, steps, blocking, destination, status, message, created, finished, real);
        try {
            return create.call(id, owner, networkId, target, count, steps, blocking, destination, status, message, created, finished, payment.addPurchases(real));
        } catch (RuntimeException | Error failure) {
            releaseReservations(network.getUnifiedStorage(), real);
            throw failure;
        }
    }

    @WrapOperation(method = "enqueueValidated(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;ILnet/minecraft/resources/ResourceLocation;JZLcom/amicbeam/beyondcraftlines/common/runtime/OrderOutputDestination;Lcom/amicbeam/beyondcraftlines/common/runtime/OrderOrigin;Lcom/amicbeam/beyondcraftlines/common/crafting/RecipePlan;)Lcom/amicbeam/beyondcraftlines/common/runtime/RecipeOrderJob;", at = @At(value = "INVOKE", target = "Lcom/amicbeam/beyondcraftlines/common/runtime/RecipeOrderSavedData;put(Lcom/amicbeam/beyondcraftlines/common/runtime/RecipeOrderJob;)V"))
    private static void projectexa$commit(RecipeOrderSavedData data, RecipeOrderJob job, Operation<Void> save, @Local(argsOnly = true) RecipePlan plan) {
        var payment = OrderFunding.current();
        if (payment == null || payment.plan() != plan) save.call(data, job);
        else payment.save(() -> save.call(data, job));
    }
}
