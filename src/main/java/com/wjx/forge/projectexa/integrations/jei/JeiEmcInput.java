package com.wjx.forge.projectexa.integrations.jei;

import com.wjx.forge.projectexa.mixin.JeiCommandUtilAccessor;
import mezz.jei.common.config.GiveMode;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.gui.util.CommandUtil;
import com.wjx.forge.projectexa.net.PacketHandler;
import com.wjx.forge.projectexa.net.packets.to_server.PacketJeiTransmute;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.SameElementInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class JeiEmcInput {
    private JeiEmcInput() { }

    public static Optional<IUserInputHandler> handle(IUserInputHandler handler, Screen screen, UserInput input,
            IInternalKeyMappings keys, CombinedRecipeFocusSource source, IIngredientManager ingredients,
            IClientToggleState toggleState, CommandUtil commandUtil) {
        if (!(screen instanceof AbstractContainerScreen<?>)) return Optional.empty();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return Optional.empty();
        IClientConfig config = ((JeiCommandUtilAccessor) (Object) commandUtil).projectexa$getClientConfig();
        boolean stackKey;
        boolean oneKey;
        // JEI's configured cheat key mappings require the cheat-mode context.
        // Enable that context only for matching, then immediately restore it.
        toggleState.setCheatItemsEnabled(true);
        try {
            stackKey = input.is(keys.getCheatItemStack());
            oneKey = !stackKey && input.is(keys.getCheatOneItem());
        } finally {
            toggleState.setCheatItemsEnabled(false);
        }
        int destination = config.getGiveMode() == GiveMode.MOUSE_PICKUP ? -1 : -2;
        boolean single = oneKey;
        if (!stackKey && !oneKey) {
            if (!config.isCheatToHotbarUsingHotkeysEnabled()) return Optional.empty();
            destination = -3;
            for (int slot = 0; slot < 9; slot++) {
                if (input.is(minecraft.options.keyHotbarSlots[slot])) { destination = slot; break; }
            }
            if (destination == -3) return Optional.empty();
        }
        final int target = destination;
        final boolean one = single;
        return source.getIngredientUnderMouse(input, keys).filter(clicked -> !clicked.getCheatItemStack(ingredients).isEmpty())
                .findFirst().map(clicked -> {
                    if (!input.isSimulate()) {
                        ItemStack stack = clicked.getCheatItemStack(ingredients);
                        PacketHandler.sendToServer(new PacketJeiTransmute(stack.copyWithCount(1),
                                minecraft.player.containerMenu.containerId, target, one));
                    }
                    return new SameElementInputHandler(handler, clicked::isMouseOver);
                });
    }
}
