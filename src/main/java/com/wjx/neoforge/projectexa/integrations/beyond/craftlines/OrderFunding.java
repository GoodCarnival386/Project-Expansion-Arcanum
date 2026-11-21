package com.wjx.neoforge.projectexa.integrations.beyond.craftlines;

import com.amicbeam.beyondcraftlines.common.crafting.*;
import com.amicbeam.beyondcraftlines.common.runtime.RecipeOrderJob;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wjx.neoforge.projectexa.integrations.beyond.EmcMaterials;
import com.wjx.neoforge.projectexa.integrations.beyond.EmcPayment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;

public final class OrderFunding {
    private static final ThreadLocal<OrderFunding> CURRENT = new ThreadLocal<>();
    private final ServerPlayer player;
    private final RecipePlan plan;
    private final EmcMaterials.Bill bill;

    private OrderFunding(ServerPlayer player, RecipePlan plan, EmcMaterials.Bill bill) {
        this.player = player;
        this.plan = plan;
        this.bill = bill;
    }

    public static OrderFunding verify(ServerPlayer player, RecipePlan nativePlan, PlanningSnapshotService.Snapshot stock,
                                      Set<String> families, RecipeResolutionOverrides choices, OrderPackets.Approval approval) {
        var account = EmcMaterials.account(player);
        if (account == null) throw stale();
        BigInteger maximum = approval.maximum();
        var missing = new LinkedHashMap<IStackKey<?>, Long>();
        for (var material : nativePlan.missing()) {
            ResourceLocation selected = choices.recipeFor(material.key());
            if (!approval.items().contains(RecipeResourceResolver.resolutionKey(material.key()))
                    || StackKeyMatch.exact(nativePlan.targetKey(), material.key())
                    || selected != null && !RecipeResolutionOverrides.NO_RECIPE.equals(selected)) throw stale();
            missing.merge(material.key(), material.amount(), Math::addExact);
        }
        var estimate = EmcMaterials.quote(account, missing);
        if (estimate == null || estimate.total().compareTo(maximum) > 0) throw stale();
        if (missing.isEmpty()) return new OrderFunding(player, nativePlan, estimate);

        // Replay the original fixed choices with a private supplement. The shared network cache never sees it.
        var withPurchases = new ArrayList<>(stock.componentEntries());
        missing.forEach((key, count) -> withPurchases.add(new PlanningSnapshotService.ComponentEntry(key, count)));
        var supplemented = new PlanningSnapshotService.Snapshot(withPurchases, stock.revision());
        RecipePlan replay = RecipePlanningService.validateFixed(player.serverLevel(), nativePlan.targetKey(), nativePlan.requested(),
                supplemented, families, choices);
        if (!replay.craftable() || !choices.completelyResolves(replay)
                || replay.steps().stream().noneMatch(step -> StackKeyMatch.exact(step.outputKey(), replay.targetKey()))) throw stale();

        var required = new LinkedHashMap<IStackKey<?>, Long>();
        replay.reserved().forEach(m -> required.merge(m.key(), m.amount(), Math::addExact));
        var real = new LinkedHashMap<IStackKey<?>, Long>();
        for (var entry : stock.componentEntries()) {
            if (required.containsKey(entry.key())) real.merge(entry.key(), entry.amount(), Math::addExact);
        }
        var allocation = OrderMaterials.split(required, real, missing);
        var physical = allocation.physical().entrySet().stream()
                .map(e -> new RecipePlan.ReservedMaterial(e.getKey(), e.getValue())).toList();
        var actual = EmcMaterials.quote(account, allocation.purchased());
        if (actual == null || actual.total().compareTo(maximum) > 0) throw stale();
        var physicalPlan = new RecipePlan(replay.targetKey(), replay.requested(), replay.steps(), List.of(), physical);
        return new OrderFunding(player, physicalPlan, actual);
    }

    public RecipePlan plan() { return plan; }

    public RecipeOrderJob enqueue(Supplier<RecipeOrderJob> nativeEnqueue) {
        if (CURRENT.get() != null) throw stale();
        CURRENT.set(this);
        try {
            return nativeEnqueue.get();
        } finally {
            CURRENT.remove();
        }
    }

    public static OrderFunding current() { return CURRENT.get(); }

    public List<RecipePlan.ReservedMaterial> addPurchases(List<RecipePlan.ReservedMaterial> physical) {
        var reserved = new LinkedHashMap<IStackKey<?>, Long>();
        physical.forEach(r -> reserved.merge(r.key(), r.amount(), Math::addExact));
        bill.items().forEach((k, n) -> reserved.merge(k, n, Math::addExact));
        return reserved.entrySet().stream().map(e -> new RecipePlan.ReservedMaterial(e.getKey(), e.getValue())).toList();
    }

    /** Called inside Craftlines' existing try/catch, after real extraction, immediately before registering the job. */
    public void save(Runnable nativeSave) {
        var account = EmcMaterials.account(player);
        var current = account == null ? null : EmcMaterials.quote(account, bill.items());
        if (current == null || current.total().compareTo(bill.total()) > 0) throw stale();
        EmcPayment.commit(current.total(), account::getEmc, account::setEmc, nativeSave);
    }

    public void sync() {
        var account = com.wjx.neoforge.projectexa.util.Util.getKnowledgeProvider(player);
        if (account != null) account.syncEmc(player);
    }

    private static IllegalStateException stale() { return new IllegalStateException("refresh the preview"); }
}
