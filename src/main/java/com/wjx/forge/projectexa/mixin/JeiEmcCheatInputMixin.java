package com.wjx.forge.projectexa.mixin;

import com.wjx.forge.projectexa.integrations.jei.JeiEmcInput;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.util.CommandUtil;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Pseudo
@Mixin(targets = "mezz.jei.gui.input.handlers.CheatInputHandler", remap = false)
public abstract class JeiEmcCheatInputMixin implements IUserInputHandler {
    @Shadow @Final private CombinedRecipeFocusSource focusSource;
    @Shadow @Final private IIngredientManager ingredientManager;
    @Shadow @Final private IClientToggleState toggleState;
    @Shadow @Final private CommandUtil commandUtil;

    @Inject(method = "handleUserInput", at = @At("HEAD"), cancellable = true, remap = false)
    private void projectexa$transmute(Screen screen, UserInput input, IInternalKeyMappings keys,
            CallbackInfoReturnable<Optional<IUserInputHandler>> callback) {
        if (!toggleState.isCheatItemsEnabled()) {
            JeiEmcInput.handle(this, screen, input, keys, focusSource, ingredientManager, toggleState, commandUtil)
                    .ifPresent(result -> callback.setReturnValue(Optional.of(result)));
        }
    }
}
