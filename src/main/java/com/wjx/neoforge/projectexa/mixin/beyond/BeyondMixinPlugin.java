package com.wjx.neoforge.projectexa.mixin.beyond;

import com.wjx.neoforge.projectexa.integrations.beyond.BeyondSupport;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class BeyondMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!BeyondSupport.terminal()) return false;
        if (mixin.contains(".craftlines.")) return BeyondSupport.orders();
        if (mixin.endsWith("JeiFillMixin")) return BeyondSupport.installed("jei", null);
        if (mixin.endsWith("EmiFillMixin") || mixin.endsWith("EmiButtonMixin")) return BeyondSupport.installed("emi", null);
        return true;
    }
    @Override public void onLoad(String pkg) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
