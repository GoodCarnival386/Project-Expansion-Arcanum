package com.wjx.neoforge.projectexa.integrations.beyond.craftlines;

import java.util.LinkedHashMap;
import java.util.Map;

/** Split the verified reservation into network extraction and new, paid items. Counts stay in long units. */
public record OrderMaterials<K>(Map<K, Long> physical, Map<K, Long> purchased) {
    public static <K> OrderMaterials<K> split(Map<K, Long> required, Map<K, Long> stored, Map<K, Long> approved) {
        var physical = new LinkedHashMap<K, Long>();
        var purchased = new LinkedHashMap<K, Long>();
        required.forEach((key, needed) -> {
            long take = Math.min(needed, stored.getOrDefault(key, 0L));
            if (take < 0 || needed < 1) throw new IllegalArgumentException("invalid reservation");
            if (take > 0) physical.put(key, take);
            long buy = needed - take;
            if (buy > approved.getOrDefault(key, 0L)) throw new IllegalStateException("refresh the preview");
            if (buy > 0) purchased.put(key, buy);
        });
        return new OrderMaterials<>(Map.copyOf(physical), Map.copyOf(purchased));
    }
}
