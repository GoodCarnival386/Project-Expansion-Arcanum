package com.wjx.neoforge.projectexa.integrations.beyond;

import net.neoforged.fml.loading.LoadingModList;

/** These integrations target the ABI of the named releases, including Craftlines' BD 0.8 variant. */
public final class BeyondSupport {
    private BeyondSupport() {}

    public static boolean installed(String id, String version) {
        return LoadingModList.get().getMods().stream().anyMatch(mod -> mod.getModId().equals(id)
                && (version == null || version.equals(mod.getVersion().toString())));
    }

    public static boolean terminal() { return installed("beyonddimensions", "0.8.10"); }
    public static boolean orders() { return terminal() && installed("beyond_craftlines", "0.7.3"); }
}
