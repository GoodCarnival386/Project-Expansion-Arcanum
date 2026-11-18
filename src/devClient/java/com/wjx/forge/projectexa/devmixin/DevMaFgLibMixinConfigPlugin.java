package com.wjx.forge.projectexa.devmixin;

import net.minecraftforge.fml.loading.FMLEnvironment;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

public final class DevMaFgLibMixinConfigPlugin implements IMixinConfigPlugin {
    private static final String TEXT_FIELD = "fi.dy.masa.malilib.gui.GuiTextFieldGeneric";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (FMLEnvironment.production) {
            return false;
        }

        boolean getter = TEXT_FIELD.equals(targetClassName);
        String methodName = getter ? "getCursorPosition" : "setCursorPosition";
        String descriptor = getter ? "()I" : "(I)V";
        String resource = TEXT_FIELD.replace('.', '/') + ".class";
        ClassLoader[] loaders = {getClass().getClassLoader(), Thread.currentThread().getContextClassLoader()};
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try (InputStream stream = loader.getResourceAsStream(resource)) {
                if (stream != null) {
                    ClassNode textField = new ClassNode();
                    new ClassReader(stream).accept(textField,
                            ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    return textField.methods.stream()
                            .anyMatch(method -> methodName.equals(method.name) && descriptor.equals(method.desc));
                }
            } catch (IOException | RuntimeException ignored) {
                // Try the other loader without loading MaFgLib's classes.
            }
        }
        return false;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
