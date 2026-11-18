package com.wjx.forge.projectexa.devmixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "fi.dy.masa.malilib.gui.GuiTextFieldGeneric", remap = false)
public abstract class DevMaFgLibTextFieldMixin extends EditBox {
    protected DevMaFgLibTextFieldMixin(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    @Inject(method = "getCursorPosition()I", at = @At("HEAD"), cancellable = true, remap = false)
    private void projectexa$readVanillaCursorPosition(CallbackInfoReturnable<Integer> callback) {
        // In official mappings, MaFgLib's getter calls itself instead of the EditBox getter.
        callback.setReturnValue(super.getCursorPosition());
    }
}
