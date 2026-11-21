package com.wjx.neoforge.projectexa.mixin.beyond;

import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wjx.neoforge.projectexa.integrations.beyond.TerminalSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = DimensionsNetMenu.class, remap = false)
public abstract class TerminalMenuMixin implements TerminalSession.Holder {
    @Unique private final TerminalSession projectexa$state = new TerminalSession();
    @Override public TerminalSession projectexa$session() { return projectexa$state; }
}
