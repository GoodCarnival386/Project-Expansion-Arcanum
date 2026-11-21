package com.wjx.neoforge.projectexa.mixin.beyond.craftlines;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.amicbeam.beyondcraftlines.client.CraftlineOrderScreen$GraphNode", remap = false)
public interface OrderNodeAccess {
    @Accessor("key") IStackKey<?> projectexa$key();
    @Accessor("depth") int projectexa$depth();
}
