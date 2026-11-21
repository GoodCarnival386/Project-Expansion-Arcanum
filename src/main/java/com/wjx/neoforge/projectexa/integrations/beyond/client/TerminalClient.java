package com.wjx.neoforge.projectexa.integrations.beyond.client;

import com.wintercogs.beyonddimensions.api.menu.workbench.StorageWorkbenchTypes;
import com.wintercogs.beyonddimensions.client.ui.InventoryScreenAccess;
import com.wintercogs.beyonddimensions.client.ui.storage.StorageWorkbenchChoice;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wjx.neoforge.projectexa.integrations.beyond.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TerminalClient {
    private static final int TOGGLE_WIDTH = 34;
    private static final int TOGGLE_HEIGHT = 12;

    private TerminalClient() {}
    private record Key(ResourceLocation id, int count) {}
    private static final Map<Key, Optional<TerminalCrafting.Fill>> cache = new HashMap<>();
    private static java.lang.ref.WeakReference<DimensionsNetMenu> cachedMenu = new java.lang.ref.WeakReference<>(null);
    private static long cachedTick;

    public static TerminalCrafting.Fill preview(DimensionsNetMenu menu, ResourceLocation id, int count) {
        var player = Minecraft.getInstance().player;
        if (player == null || !TerminalCrafting.active(menu, player)) return null;
        long tick = player.level().getGameTime();
        if (cachedMenu.get() != menu || cachedTick != tick) {
            cachedMenu = new java.lang.ref.WeakReference<>(menu);
            cachedTick = tick;
            cache.clear();
        }
        return cache.computeIfAbsent(new Key(id, count), k -> Optional.ofNullable(count == 0
                ? TerminalCrafting.quoteOne(menu, player, id) : TerminalCrafting.plan(menu, player, id, count))).orElse(null);
    }

    public static List<Component> unavailable(DimensionsNetMenu menu, ResourceLocation id) {
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("projectexa.beyond.unavailable"));
        var one = preview(menu, id, 0);
        if (one != null) lines.addAll(one.bill().tooltip());
        return lines;
    }

    public static void submit(DimensionsNetMenu menu, TerminalCrafting.Fill fill) {
        PacketDistributor.sendToServer(new TerminalPackets.Fill(menu.containerId, fill.id(), fill.batches(), fill.bill().total()));
        StorageWorkbenchChoice.reveal(StorageWorkbenchTypes.CRAFTING);
        cache.clear();
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TerminalClient::drawToggle);
        NeoForge.EVENT_BUS.addListener(TerminalClient::clickToggle);
    }

    private static Rect2i toggleBounds(Screen screen, DimensionsNetMenu menu) {
        if (InventoryScreenAccess.menu(screen) != menu || menu.workbenchWidget(StorageWorkbenchTypes.CRAFTING) == null) return null;
        Rect2i result = InventoryScreenAccess.slot(screen, menu.getSlot(menu.crafting().resultSlot()));
        Rect2i gridBottom = InventoryScreenAccess.slot(screen, menu.crafting().inputSlots().getLast());
        if (result == null || gridBottom == null) return null;

        // The result sits at the middle of the three grid rows. Use the space beneath its frame.
        int x = result.getX() + result.getWidth() + 2 - TOGGLE_WIDTH;
        int y = result.getY() + result.getHeight() + 4;
        if (x < gridBottom.getX() + gridBottom.getWidth() || x + TOGGLE_WIDTH > screen.width
                || y < 0 || y + TOGGLE_HEIGHT > Math.min(screen.height, gridBottom.getY() + gridBottom.getHeight())) return null;
        return new Rect2i(x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT);
    }

    private static void drawToggle(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || !(screen.getMenu() instanceof DimensionsNetMenu menu)) return;
        Rect2i box = toggleBounds(screen, menu);
        if (box == null) return;
        boolean on = TerminalSession.of(menu).enabled;
        var draw = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        int x = box.getX(), y = box.getY();
        draw.fill(x, y, x + box.getWidth(), y + box.getHeight(), on ? 0xff316744 : 0xff404047);
        draw.drawCenteredString(font, on ? "EMC +" : "EMC −", x + box.getWidth() / 2, y + 2, 0xffffff);
        if (box.contains(event.getMouseX(), event.getMouseY()))
            draw.renderTooltip(font, Component.translatable("projectexa.beyond.terminal_toggle"), event.getMouseX(), event.getMouseY());
    }

    private static void clickToggle(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof DimensionsNetMenu menu)) return;
        Rect2i box = toggleBounds(screen, menu);
        if (box == null || !box.contains((int)event.getMouseX(), (int)event.getMouseY())) return;
        event.setCanceled(true);
        var player = Minecraft.getInstance().player;
        var state = TerminalSession.of(menu);
        if (!state.enabled && EmcMaterials.account(player) == null) {
            if (player != null) player.displayClientMessage(Component.translatable("projectexa.beyond.tablet"), true);
            return;
        }
        state.enabled = !state.enabled;
        cache.clear();
        PacketDistributor.sendToServer(new TerminalPackets.Toggle(menu.containerId, state.enabled));
    }
}
