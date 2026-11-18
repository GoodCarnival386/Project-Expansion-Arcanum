package com.wjx.forge.projectexa.integrations.forgematica;

import com.wjx.forge.projectexa.net.packets.to_server.PacketOpenArcaneTransmutationTablet;
import com.wjx.forge.projectexa.registries.Items;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** MaFgLib's button background and input handling, with the actual tablet item as its icon. */
public final class ForgematicaMaterialButton extends ButtonGeneric {
    public static final int TEXT_OFFSET = 23;

    private final ItemStack tabletIcon = new ItemStack(Items.ARCANE_TRANSMUTATION_TABLET.get());
    private final String label;

    public ForgematicaMaterialButton(int x, int y, int width, String label) {
        super(x, y, width, 20, "");
        this.label = label;
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return PacketOpenArcaneTransmutationTablet.hasTablet(mc.player) && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public void render(int mouseX, int mouseY, boolean selected, GuiGraphics graphics) {
        visible = PacketOpenArcaneTransmutationTablet.hasTablet(mc.player);
        if (!visible) {
            hovered = false;
            return;
        }

        super.render(mouseX, mouseY, selected, graphics);
        graphics.renderItem(tabletIcon, x + 4, y + 2);
        drawStringWithShadow(x + TEXT_OFFSET, y + 6, hovered ? 0xFFFFA0 : 0xE0E0E0, label, graphics);
    }
}
