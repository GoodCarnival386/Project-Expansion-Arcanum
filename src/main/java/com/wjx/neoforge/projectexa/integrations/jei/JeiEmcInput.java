package com.wjx.neoforge.projectexa.integrations.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.wjx.neoforge.projectexa.config.JeiEmcClientState;
import com.wjx.neoforge.projectexa.net.packets.to_server.PacketJeiTransmute;
import com.wjx.neoforge.projectexa.util.ArcaneTabletAccess;
import com.wjx.neoforge.projectexa.util.Util;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.common.Internal;
import mezz.jei.common.config.GiveMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/** Shares JEI's bindings and give mode without depending on its version-specific input handlers. */
public final class JeiEmcInput {
    private static boolean registered;
    private static final Map<Integer, Screen> HANDLED_MOUSE_BUTTONS = new HashMap<>();

    private JeiEmcInput() {}

    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, JeiEmcInput::onMouse);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, JeiEmcInput::onMouseReleased);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, JeiEmcInput::onMouseDragged);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, JeiEmcInput::onKey);
    }

    private static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        HANDLED_MOUSE_BUTTONS.remove(event.getButton());
        if (handle(event.getScreen(), InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) {
            HANDLED_MOUSE_BUTTONS.put(event.getButton(), event.getScreen());
            event.setCanceled(true);
        }
    }

    private static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        // The server may put the purchase on the cursor before this release arrives.
        // Vanilla must not interpret the rest of that JEI click as placing or dropping it.
        if (HANDLED_MOUSE_BUTTONS.remove(event.getButton()) == event.getScreen()) {
            event.setCanceled(true);
        }
    }

    private static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (HANDLED_MOUSE_BUTTONS.get(event.getMouseButton()) == event.getScreen()) {
            event.setCanceled(true);
        }
    }

    public static void clearMouseInput() {
        HANDLED_MOUSE_BUTTONS.clear();
    }

    private static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (handle(event.getScreen(), InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            event.setCanceled(true);
        }
    }

    private static boolean handle(Screen screen, InputConstants.Key key) {
        var runtime = JeiPlugin.RUNTIME;
        Minecraft mc = Minecraft.getInstance();
        if (runtime == null || !JeiEmcClientState.isEnabled() || !(screen instanceof AbstractContainerScreen<?>)
                || mc.player == null || mc.player.isSpectator() || !ArcaneTabletAccess.hasTablet(mc.player)
                || key.getType() != InputConstants.Type.MOUSE && (runtime.getIngredientListOverlay().hasKeyboardFocus()
                || screen.getFocused() instanceof EditBox edit && edit.isFocused())) return false;
        ItemStack hovered = runtime.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (hovered == null || hovered.isEmpty()) {
            hovered = runtime.getBookmarkOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        }
        // Only JEI's ingredient list and bookmarks are purchase sources. Container
        // slots, including the player's inventory and tablet outputs, stay vanilla.
        if (hovered == null || hovered.isEmpty()) return false;
        var toggle = Internal.getClientToggleState();
        if (toggle.isCheatItemsEnabled()) return false;
        var config = Internal.getClientConfigs().getClientConfig();
        var keys = Internal.getKeyMappings();
        boolean stackKey;
        boolean oneKey;
        // JEI's cheat bindings use the cheat-mode context even for ordinary EMC purchases.
        toggle.setCheatItemsEnabled(true);
        try {
            stackKey = keys.getCheatItemStack().isActiveAndMatches(key);
            oneKey = !stackKey && keys.getCheatOneItem().isActiveAndMatches(key);
        } finally {
            toggle.setCheatItemsEnabled(false);
        }
        int destination = config.giveMode().get() == GiveMode.MOUSE_PICKUP ? -1 : -2;
        if (!stackKey && !oneKey) {
            if (!config.cheatToHotbarUsingHotkeysEnabled().get()) return false;
            destination = -3;
            for (int i = 0; i < 9; i++) {
                if (mc.options.keyHotbarSlots[i].isActiveAndMatches(key)) { destination = i; break; }
            }
            if (destination == -3) return false;
        }
        ItemStack clean = Util.cleanStack(hovered);
        var provider = Util.getKnowledgeProvider(mc.player);
        if (clean.isEmpty() || provider == null || !provider.hasKnowledge(clean)) return false;
        long value = moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(clean);
        if (value <= 0) return false;
        if (provider.getEmc().compareTo(BigInteger.valueOf(value)) < 0) {
            mc.player.sendSystemMessage(Component.translatable("text.projectexa.jei_transmute.insufficient_emc", Long.toString(value))
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        boolean creativeCursor = destination == -1 && screen instanceof CreativeModeInventoryScreen;
        PacketDistributor.sendToServer(new PacketJeiTransmute(hovered.copyWithCount(1), mc.player.containerMenu.containerId,
                destination, oneKey, creativeCursor, creativeCursor ? mc.player.containerMenu.getCarried().copy() : ItemStack.EMPTY));
        return true;
    }
}
