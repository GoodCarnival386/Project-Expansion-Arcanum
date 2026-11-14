package com.wjx.forge.projectexa.mixin;

import mezz.jei.common.config.IClientConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "mezz.jei.gui.util.CommandUtil", remap = false)
public interface JeiCommandUtilAccessor {
    @Accessor(value = "clientConfig", remap = false)
    IClientConfig projectexa$getClientConfig();
}
