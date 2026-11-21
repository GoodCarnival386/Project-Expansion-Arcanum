package com.wjx.neoforge.projectexa.integrations.beyond;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Assign one exact stack type to each occupied grid slot. A shared stock of a type can be used only once. */
public final class GridAllocation<K> {
    public record Supply<K>(K key, int slots, long stock, long unitPrice, int stackLimit) {}
    public record Placement<K>(K key, int slots, int purchased) {}
    public record Plan<K>(int batches, BigInteger cost, List<Placement<K>> placements) {}
    private record Path<K>(Placement<K> head, Path<K> tail) {}

    private final int slots;
    private final List<Supply<K>> supplies;
    private final BigInteger balance;

    public GridAllocation(int slots, List<Supply<K>> supplies, BigInteger balance) {
        this.slots = slots;
        this.supplies = supplies;
        this.balance = balance;
    }

    public Plan<K> maximum(int requested) {
        int high = Math.min(requested, supplies.stream().mapToInt(Supply::stackLimit).max().orElse(0));
        Plan<K> best = null;
        int low = 1;
        while (low <= high && slots != 0) {
            int count = low + (high - low) / 2;
            Plan<K> plan = allocate(count, balance);
            if (plan == null) high = count - 1;
            else { best = plan; low = count + 1; }
        }
        return best;
    }

    /** Price a complete single grid for the tooltip even if the player cannot afford it. */
    public Plan<K> quoteOne() {
        return slots == 0 ? null : allocate(1, null);
    }

    private Plan<K> allocate(int count, BigInteger budget) {
        // Nine slots bound the state space to 512 even for tags with many alternatives.
        BigInteger[] costs = new BigInteger[slots + 1];
        List<Path<K>> paths = new ArrayList<>(java.util.Collections.nCopies(slots + 1, null));
        costs[0] = BigInteger.ZERO;
        for (Supply<K> supply : supplies) {
            if (supply.stackLimit < count || supply.slots == 0) continue;
            BigInteger[] extra = new BigInteger[10];
            for (int n = 1; n <= Integer.bitCount(supply.slots); n++) {
                long missing = Math.max(0L, (long)n * count - supply.stock);
                if (missing == 0 || supply.unitPrice > 0)
                    extra[n] = BigInteger.valueOf(supply.unitPrice).multiply(BigInteger.valueOf(missing));
            }
            // Descending traversal prevents assigning the same supply twice.
            for (int used = slots - 1; used >= 0; used--) {
                if (costs[used] == null) continue;
                int free = supply.slots & ~used;
                for (int part = free; part != 0; part = (part - 1) & free) {
                    int n = Integer.bitCount(part);
                    if (extra[n] == null) continue;
                    int next = used | part;
                    BigInteger cost = costs[used].add(extra[n]);
                    if ((budget != null && cost.compareTo(budget) > 0) || (costs[next] != null && cost.compareTo(costs[next]) >= 0)) continue;
                    costs[next] = cost;
                    int purchased = (int)Math.max(0L, (long)n * count - supply.stock);
                    paths.set(next, new Path<>(new Placement<>(supply.key, part, purchased), paths.get(used)));
                }
            }
            if (BigInteger.ZERO.equals(costs[slots])) break;
        }
        if (costs[slots] == null) return null;
        List<Placement<K>> result = new ArrayList<>();
        for (Path<K> p = paths.get(slots); p != null; p = p.tail) result.add(p.head);
        return new Plan<>(count, costs[slots], List.copyOf(result));
    }
}
