package com.wjx.forge.projectexa.config;

import com.wjx.forge.projectexa.Main;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client input remains untouched until the server has supplied its setting. */
@Mod.EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class JeiEmcClientState {
    private static volatile boolean enabled;
    private static volatile boolean worldPickEnabled;

    private JeiEmcClientState() { }
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static boolean isWorldPickEnabled() { return worldPickEnabled; }
    public static void setWorldPickEnabled(boolean value) { worldPickEnabled = value; }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        enabled = false;
        worldPickEnabled = false;
    }
}
