package com.wjx.forge.projectexa.devmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EditBox.class, remap = false)
public abstract class DevMaFgLibEditBoxMixin {
    @Shadow private int cursorPos;
    @Shadow private String value;

    @WrapOperation(
            method = "moveCursorTo(I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;setCursorPosition(I)V"),
            remap = false
    )
    private void projectexa$setCursorWithoutMaFgLibRecursion(EditBox editBox, int position, Operation<Void> original) {
        if (editBox instanceof GuiTextFieldGeneric) {
            // Official mappings make MaFgLib's wrapper override this method and call moveCursorTo again.
            // Apply the vanilla cursor clamp here so moveCursorTo can still update selection and notify listeners.
            this.cursorPos = Mth.clamp(position, 0, this.value.length());
        } else {
            original.call(editBox, position);
        }
    }
}
