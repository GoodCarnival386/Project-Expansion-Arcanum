package com.wjx.forge.projectexa.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.crafting.plan.PlanMaterialTable$Row", remap = false)
public interface RsiMaterialTableRowAccessor {
    @Accessor("leftover") boolean projectexa$leftover();
}
