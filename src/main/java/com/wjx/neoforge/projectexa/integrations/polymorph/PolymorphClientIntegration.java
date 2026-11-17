package com.wjx.neoforge.projectexa.integrations.polymorph;

import com.illusivesoulworks.polymorph.api.client.PolymorphWidgets;
import com.illusivesoulworks.polymorph.api.client.widgets.PlayerRecipesWidget;
import com.wjx.neoforge.projectexa.Main;
import com.wjx.neoforge.projectexa.gui.GUIArcaneTransmutationTablet;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Main.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PolymorphClientIntegration {
    private PolymorphClientIntegration() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("polymorph")) event.enqueueWork(Widgets::register);
    }

    // NeoForge loads the subscriber before setup runs. Keep Polymorph types,
    // including the widget lambda's inferred return type, in a lazily loaded class.
    private static final class Widgets {
        private static void register() {
            PolymorphWidgets.getInstance().registerWidget(screen -> {
                if (!(screen instanceof GUIArcaneTransmutationTablet tablet)) return null;
                var slot = PolymorphWidgets.getInstance().findResultSlot(screen);
                if (slot == null) return null;
                return new PlayerRecipesWidget(screen, slot) {
                    @Override public int getXPos() { return -70; }
                    @Override public int getYPos() { return 78; }
                    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
                        if (!tablet.isCraftingPanelVisible()) return;
                        if (tablet.isStationPanelInteractive()) {
                            super.render(graphics, x, y, partialTick);
                            return;
                        }
                        tablet.beginStationPanelRender(graphics);
                        try {
                            super.render(graphics, x, y, partialTick);
                        } finally {
                            tablet.endStationPanelRender(graphics);
                        }
                    }
                    @Override public boolean mouseClicked(double x, double y, int button) {
                        return tablet.isCraftingPanelVisible() && tablet.isStationPanelInteractive()
                                && super.mouseClicked(x, y, button);
                    }
                };
            });
        }
    }
}
