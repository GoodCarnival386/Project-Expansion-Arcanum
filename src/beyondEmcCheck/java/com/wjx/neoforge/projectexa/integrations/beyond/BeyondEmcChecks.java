package com.wjx.neoforge.projectexa.integrations.beyond;

import com.wjx.neoforge.projectexa.integrations.beyond.craftlines.OrderMaterials;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone checks for the production settlement code, using only the JDK. */
public final class BeyondEmcChecks {
    public static void main(String[] args) {
        nineSlotsAndSharedBalance();
        overlappingIngredients();
        slotsAndLargePrices();
        orderReservationsAndCancellation();
        rejectedSettlement();
        compareWithExhaustiveSearch();
    }

    private static void nineSlotsAndSharedBalance() {
        var material = new GridAllocation.Supply<>("iron", 511, 0, 256, 64);
        var empty = new GridAllocation<>(511, List.of(material), BigInteger.valueOf(2303));
        equal(null, empty.maximum(1));
        equal(BigInteger.valueOf(2304), empty.quoteOne().cost());
        var funded = new GridAllocation<>(511, List.of(material), BigInteger.valueOf(2304));
        equal(1, funded.maximum(64).batches());
        equal(9, funded.maximum(1).placements().getFirst().purchased());

        var mixed = new GridAllocation<>(511, List.of(new GridAllocation.Supply<>("iron", 511, 7, 256, 64)), BigInteger.valueOf(512));
        equal(BigInteger.valueOf(512), mixed.maximum(64).cost());
        equal(2, mixed.maximum(64).placements().getFirst().purchased());
    }

    private static void overlappingIngredients() {
        // Slot one allows either wood, slot two specifically requires oak. This has a zero-EMC solution.
        var planner = new GridAllocation<>(3, List.of(
                new GridAllocation.Supply<>("oak", 3, 1, 8, 64),
                new GridAllocation.Supply<>("birch", 1, 1, 0, 64)), BigInteger.ZERO);
        equal(BigInteger.ZERO, planner.maximum(1).cost());
        equal(2, planner.maximum(1).placements().size());
        equal(null, new GridAllocation<>(3, List.of(new GridAllocation.Supply<>("oak", 3, 1, 0, 64)),
                BigInteger.TEN).maximum(1));
    }

    private static void slotsAndLargePrices() {
        BigInteger large = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        var plan = new GridAllocation<>(3, List.of(new GridAllocation.Supply<>("unique", 3, 0, Long.MAX_VALUE, 1)), large).maximum(99);
        equal(1, plan.batches());
        equal(large, plan.cost());
        equal(16, new GridAllocation<>(1, List.of(new GridAllocation.Supply<>("pearl", 1, Long.MAX_VALUE, 0, 16)),
                BigInteger.ZERO).maximum(Integer.MAX_VALUE).batches());
    }

    private static void orderReservationsAndCancellation() {
        var demand = Map.of("iron", 20L, "chest", 4L);
        var stock = Map.of("iron", 20L, "chest", 2L);
        var allocation = OrderMaterials.split(demand, stock, Map.of("chest", 2L));
        equal(stock, allocation.physical());
        equal(Map.of("chest", 2L), allocation.purchased());
        // The native order owns one reservation containing both sources. It can return the remaining real items.
        var reserved = new java.util.HashMap<>(allocation.physical());
        allocation.purchased().forEach((k, n) -> reserved.merge(k, n, Math::addExact));
        equal(demand, reserved);
        equal(Map.of(), OrderMaterials.split(demand, demand, Map.of("chest", 2L)).purchased());
        rejects(() -> OrderMaterials.split(demand, Map.of("iron", 20L), Map.of("chest", 2L)));
        equal(Long.MAX_VALUE - 1, OrderMaterials.split(Map.of("stone", Long.MAX_VALUE), Map.of("stone", 1L),
                Map.of("stone", Long.MAX_VALUE - 1)).purchased().get("stone"));
    }

    private static void rejectedSettlement() {
        var balance = new AtomicReference<>(BigInteger.TEN);
        rejects(() -> EmcPayment.commit(BigInteger.TWO, balance::get, balance::set, () -> {
            equal(BigInteger.valueOf(8), balance.get());
            // An unrelated deposit during delivery must survive rollback.
            balance.updateAndGet(value -> value.add(BigInteger.ONE));
            throw new IllegalStateException("order rejected");
        }));
        equal(BigInteger.valueOf(11), balance.get());
        EmcPayment.commit(BigInteger.TEN, balance::get, balance::set, () -> {});
        equal(BigInteger.ONE, balance.get());
        rejects(() -> EmcPayment.commit(BigInteger.TEN, balance::get, balance::set, () -> {
            throw new AssertionError("an unfunded order was delivered");
        }));
        equal(BigInteger.ONE, balance.get());
    }

    private static void compareWithExhaustiveSearch() {
        var random = new Random(5511);
        for (int test = 0; test < 250; test++) {
            int slots = 1 + random.nextInt(4), mask = (1 << slots) - 1, budget = random.nextInt(25);
            var supplies = new java.util.ArrayList<GridAllocation.Supply<Integer>>();
            for (int i = 0; i < 4; i++)
                supplies.add(new GridAllocation.Supply<>(i, 1 + random.nextInt(mask), random.nextInt(8), random.nextInt(6), 1 + random.nextInt(4)));
            var result = new GridAllocation<>(mask, supplies, BigInteger.valueOf(budget)).maximum(4);
            int expectedCount = 0, expectedCost = 0;
            for (int n = 1; n <= 4; n++) {
                int cost = exhaustive(supplies, slots, n, 0, new int[supplies.size()]);
                if (cost <= budget) { expectedCount = n; expectedCost = cost; }
            }
            equal(expectedCount, result == null ? 0 : result.batches());
            if (result != null) equal(BigInteger.valueOf(expectedCost), result.cost());
        }
    }

    private static int exhaustive(List<GridAllocation.Supply<Integer>> sources, int slots, int count, int slot, int[] used) {
        if (slot == slots) {
            int cost = 0;
            for (int i = 0; i < sources.size(); i++) {
                var key = sources.get(i);
                int missing = (int)Math.max(0, used[i] - key.stock());
                if (missing > 0 && key.unitPrice() == 0) return Integer.MAX_VALUE;
                cost += (int)(missing * key.unitPrice());
            }
            return cost;
        }
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < sources.size(); i++) {
            var key = sources.get(i);
            if ((key.slots() & (1 << slot)) == 0 || count > key.stackLimit()) continue;
            used[i] += count;
            best = Math.min(best, exhaustive(sources, slots, count, slot + 1, used));
            used[i] -= count;
        }
        return best;
    }

    private static void equal(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void rejects(Runnable op) {
        try { op.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Expected a rejected operation");
    }
}
