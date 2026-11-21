package com.wjx.neoforge.projectexa.integrations.beyond.client;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.network.chat.Component;
import java.util.List;

public record TerminalJeiHint(Type getType, List<Component> lines) implements IRecipeTransferError {
    @Override public int getButtonHighlightColor() { return 0; }
    @Override public void getTooltip(ITooltipBuilder tooltip) { tooltip.addAll(lines); }
}
