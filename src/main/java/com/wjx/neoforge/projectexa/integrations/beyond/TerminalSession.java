package com.wjx.neoforge.projectexa.integrations.beyond;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayDeque;
import java.util.List;

public final class TerminalSession {
    public boolean enabled;
    public ResourceLocation lastRecipe;
    public List<ItemStack> afterTake = List.of();
    final ArrayDeque<ItemStack> displaced = new ArrayDeque<>();
    boolean taking, gridChanged, emcChanged;

    public interface Holder {
        TerminalSession projectexa$session();
    }
    public static TerminalSession of(Object menu) { return ((Holder)menu).projectexa$session(); }
}
