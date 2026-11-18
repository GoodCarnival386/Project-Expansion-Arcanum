package com.wjx.forge.projectexa.mixin;

import com.wjx.forge.projectexa.integrations.forgematica.ForgematicaMaterialButton;
import com.wjx.forge.projectexa.integrations.forgematica.ForgematicaMaterialListRefresh;
import com.wjx.forge.projectexa.net.PacketHandler;
import com.wjx.forge.projectexa.net.packets.to_server.PacketForgematicaTransmute;
import com.wjx.forge.projectexa.net.packets.to_server.PacketOpenArcaneTransmutationTablet;
import fi.dy.masa.litematica.gui.widgets.WidgetListMaterialList;
import fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListSorter;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.malilib.gui.GuiListBase;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.gui.GuiMaterialList", remap = false)
public abstract class ForgematicaMaterialListMixin
        extends GuiListBase<MaterialListEntry, WidgetMaterialListEntry, WidgetListMaterialList>
        implements ForgematicaMaterialListRefresh {
    @Unique
    private static final String PROJECTEXA_LABEL = "gui.projectexa.forgematica.transmute";
    @Unique
    private static final String PROJECTEXA_MULTIPLIER_LABEL = "litematica.gui.label.material_list.multiplier";

    protected ForgematicaMaterialListMixin(int listX, int listY) {
        super(listX, listY);
    }

    @Shadow(remap = false)
    public abstract MaterialListBase getMaterialList();

    @Shadow(remap = false)
    private int getElementTotalWidth() {
        throw new AssertionError();
    }

    @Inject(method = "initGui()V", at = @At("TAIL"), remap = false)
    private void projectexa$addTransmuteButton(CallbackInfo callback) {
        if (!PacketOpenArcaneTransmutationTablet.hasTablet(mc.player)) {
            return;
        }

        String label = Component.translatable(PROJECTEXA_LABEL).getString();
        int buttonWidth = projectexa$buttonWidth(label);
        boolean lowerRow = projectexa$needsLowerRow(buttonWidth);
        int multiplierWidth = getStringWidth(Component.translatable(PROJECTEXA_MULTIPLIER_LABEL).getString());
        int x = lowerRow ? width - buttonWidth - 12 : width - multiplierWidth - 64 - buttonWidth;
        int y = lowerRow ? height - 58 : 24;
        addButton(new ForgematicaMaterialButton(Math.max(12, x), y, buttonWidth, label), (button, mouseButton) -> {
            if (mouseButton == 0) {
                projectexa$transmute();
            }
        });
    }

    @Inject(method = "getBrowserHeight()I", at = @At("RETURN"), cancellable = true, remap = false)
    private void projectexa$reserveLowerRow(CallbackInfoReturnable<Integer> callback) {
        if (PacketOpenArcaneTransmutationTablet.hasTablet(mc.player)
                && projectexa$needsLowerRow(projectexa$buttonWidth(Component.translatable(PROJECTEXA_LABEL).getString()))) {
            callback.setReturnValue(Math.max(0, callback.getReturnValueI() - 24));
        }
    }

    @Unique
    private int projectexa$buttonWidth(String label) {
        return ForgematicaMaterialButton.TEXT_OFFSET + getStringWidth(label) + 6;
    }

    @Unique
    private boolean projectexa$needsLowerRow(int buttonWidth) {
        return width < getElementTotalWidth() + buttonWidth + 12;
    }

    @Unique
    private void projectexa$transmute() {
        if (!PacketOpenArcaneTransmutationTablet.hasTablet(mc.player)) {
            return;
        }

        projectexa$refreshAvailableMaterials();
        MaterialListBase materials = getMaterialList();
        int multiplier = materials.getMultiplier();
        List<MaterialListEntry> entries = new ArrayList<>(materials.getMaterialsFiltered(false));
        entries.sort(new MaterialListSorter(materials));
        List<PacketForgematicaTransmute.Demand> demands = new ArrayList<>();
        for (MaterialListEntry entry : entries) {
            ItemStack stack = entry.getStack();
            long missing = multiplier == 1 ? entry.getCountMissing() : (long) multiplier * entry.getCountTotal();
            long shortage = missing - entry.getCountAvailable();
            if (!stack.isEmpty() && shortage > 0) {
                demands.add(new PacketForgematicaTransmute.Demand(stack.copyWithCount(1), missing, shortage));
            }
        }

        if (demands.isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("text.projectexa.forgematica.nothing_missing"), false);
        } else if (demands.size() > PacketForgematicaTransmute.MAX_MATERIALS) {
            mc.player.displayClientMessage(Component.translatable("text.projectexa.forgematica.list_too_large"), false);
        } else {
            PacketHandler.sendToServer(new PacketForgematicaTransmute(demands));
        }
    }

    @Override
    public void projectexa$refreshAvailableMaterials() {
        if (mc.player != null) {
            MaterialListBase materials = getMaterialList();
            MaterialListUtils.updateAvailableCounts(materials.getMaterialsAll(), mc.player);
            materials.refreshPreFilteredList();
            getListWidget().refreshEntries();
        }
    }
}
