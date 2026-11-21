package com.wjx.neoforge.projectexa.integrations.beyond;

import com.wintercogs.beyonddimensions.api.menu.workbench.PlayerWorkbenches;
import com.wintercogs.beyonddimensions.api.menu.workbench.StorageWorkbenchTypes;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.CraftingWidget;
import com.wjx.neoforge.projectexa.util.Util;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;

public final class TerminalCrafting {
    private TerminalCrafting() {}
    private static final class ChangedResult extends RuntimeException {}

    public record Fill(ResourceLocation id, int batches, List<ItemStack> grid,
                       Map<ItemStackKey, Integer> physical, EmcMaterials.Bill bill, ItemStack output) {}

    public static boolean active(DimensionsNetMenu menu, Player player) {
        return TerminalSession.of(menu).enabled && player != null && player.containerMenu == menu
                && menu.workbenchWidget(StorageWorkbenchTypes.CRAFTING) instanceof CraftingWidget
                && com.wjx.neoforge.projectexa.util.ArcaneTabletAccess.hasTablet(player);
    }

    public static @Nullable Fill plan(DimensionsNetMenu menu, Player player, @Nullable ResourceLocation id, int requested) {
        return plan(menu, player, id, requested, false);
    }

    public static @Nullable Fill quoteOne(DimensionsNetMenu menu, Player player, ResourceLocation id) {
        return plan(menu, player, id, 1, true);
    }

    private static @Nullable Fill plan(DimensionsNetMenu menu, Player player, @Nullable ResourceLocation id, int requested, boolean quoteOnly) {
        if (id == null || requested < 1 || !active(menu, player)) return null;
        var account = EmcMaterials.account(player);
        var holder = player.level().getRecipeManager().byKey(id).orElse(null);
        if (account == null || holder == null || !(holder.value() instanceof CraftingRecipe recipe)) return null;
        List<Ingredient> inputs = recipe.getIngredients();
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        if (inputs.isEmpty() || width < 1 || width > 3 || inputs.size() > width * 3) return null;

        var local = new LinkedHashMap<ItemStackKey, Integer>();
        for (var slot : menu.crafting().inputSlots()) count(local, slot.getItem());
        player.getInventory().items.forEach(stack -> count(local, stack));
        var storage = menu.crafting().storage();
        Set<ItemStackKey> candidates = new LinkedHashSet<>(local.keySet());
        Set<ItemStackKey> queried = new HashSet<>();
        int mask = 0;
        for (int i = 0; i < inputs.size(); i++) {
            Ingredient input = inputs.get(i);
            if (input.isEmpty()) continue;
            mask |= 1 << gridIndex(i, width);
            for (ItemStack option : input.getItems()) {
                if (option.isEmpty()) continue;
                ItemStackKey key = new ItemStackKey(option);
                candidates.add(key);
                ItemStack clean = Util.cleanStack(option);
                if (!clean.isEmpty()) candidates.add(new ItemStackKey(clean));
                if (queried.add(key)) {
                    for (KeyAmount match : storage.getFuzzyMatches(key))
                        if (match.key() instanceof ItemStackKey variant) candidates.add(variant);
                }
            }
        }

        List<GridAllocation.Supply<ItemStackKey>> supplies = new ArrayList<>();
        for (ItemStackKey key : candidates) {
            ItemStack stack = key.getReadOnlyStack();
            int eligible = 0;
            for (int i = 0; i < inputs.size(); i++)
                if (!inputs.get(i).isEmpty() && inputs.get(i).test(stack)) eligible |= 1 << gridIndex(i, width);
            if (eligible == 0) continue;
            // The grid can hold at most nine stacks, even when the network uses long quantities.
            long stock = Math.min(Integer.MAX_VALUE, storage.getStackByKey(key).amount()) + local.getOrDefault(key, 0);
            long price = EmcMaterials.price(account, key);
            if (stock > 0 || price > 0) supplies.add(new GridAllocation.Supply<>(key, eligible, stock, price, stack.getMaxStackSize()));
        }
        var allocation = new GridAllocation<>(mask, supplies, account.getEmc());
        var assignment = quoteOnly ? allocation.quoteOne() : allocation.maximum(requested);
        if (assignment == null) return null;
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        var physical = new LinkedHashMap<ItemStackKey, Integer>();
        var bought = new LinkedHashMap<ItemStackKey, Long>();
        for (var part : assignment.placements()) {
            int needed = Integer.bitCount(part.slots()) * assignment.batches();
            physical.put(part.key(), needed - part.purchased());
            if (part.purchased() > 0) bought.put(part.key(), (long)part.purchased());
            for (int i = 0; i < 9; i++)
                if ((part.slots() & (1 << i)) != 0) grid.set(i, part.key().copyStackWithCount(assignment.batches()));
        }
        var input = CraftingInput.of(3, 3, grid);
        if (!recipe.matches(input, player.level())) return null;
        ItemStack output = recipe.assemble(input, player.registryAccess());
        if (output.isEmpty() || !output.isItemEnabled(player.level().enabledFeatures())) return null;
        return new Fill(id, assignment.batches(), List.copyOf(grid), physical,
                new EmcMaterials.Bill(bought, assignment.cost()), output);
    }

    private static int gridIndex(int i, int width) { return i / width * 3 + i % width; }

    private static void count(Map<ItemStackKey, Integer> stock, ItemStack stack) {
        if (!stack.isEmpty()) stock.merge(new ItemStackKey(stack), stack.getCount(), Integer::sum);
    }

    private static int take(List<ItemStack> source, ItemStackKey key, int remaining) {
        for (ItemStack stack : source) {
            if (remaining == 0) break;
            if (!ItemStack.isSameItemSameComponents(stack, key.getReadOnlyStack())) continue;
            int count = Math.min(remaining, stack.getCount());
            stack.shrink(count);
            remaining -= count;
        }
        return remaining;
    }

    /** Stage local consumption on copies. A short network read rolls back before changing the grid or account. */
    public static boolean apply(DimensionsNetMenu menu, ServerPlayer player, Fill fill, BigInteger limit) {
        IKnowledgeProvider account = EmcMaterials.account(player);
        if (account == null || !active(menu, player) || !menu.stillValid(player) || fill.bill.total().compareTo(limit) > 0) return false;
        CraftingWidget widget = menu.crafting();
        List<Slot> slots = widget.inputSlots();
        var oldGrid = slots.stream().map(s -> s.getItem().copy()).toList();
        var inventory = player.getInventory().items;
        var oldInventory = inventory.stream().map(ItemStack::copy).toList();
        var gridRemainder = oldGrid.stream().map(ItemStack::copy).toList();
        var invRemainder = oldInventory.stream().map(ItemStack::copy).toList();

        List<KeyAmount> withdrawn = new ArrayList<>();
        IStackHandler storage = widget.storage();
        boolean committed = false;
        try {
            for (var entry : fill.physical.entrySet()) {
                int need = take(gridRemainder, entry.getKey(), entry.getValue());
                need = take(invRemainder, entry.getKey(), need);
                if (need == 0) continue;
                KeyAmount got = storage.extract(entry.getKey(), need, false, false);
                if (got.amount() > 0) withdrawn.add(got);
                if (got.amount() != need || !got.key().isSameTypeSameComponents(entry.getKey())) return false;
            }
            EmcMaterials.Bill current = EmcMaterials.quote(account, fill.bill.items());
            if (current == null || current.total().compareTo(limit) > 0 || !active(menu, player) || !menu.stillValid(player)
                    || !ItemStack.listMatches(oldInventory, inventory)
                    || !ItemStack.listMatches(oldGrid, slots.stream().map(Slot::getItem).toList())) return false;
            EmcPayment.commit(current.total(), account::getEmc, account::setEmc, () -> {
                try {
                    widget.consumeIngredients(() -> {
                        for (int i = 0; i < inventory.size(); i++)
                            if (!ItemStack.matches(oldInventory.get(i), invRemainder.get(i))) inventory.set(i, invRemainder.get(i));
                        for (int i = 0; i < slots.size(); i++) slots.get(i).set(fill.grid.get(i).copy());
                    });
                    if (!ItemStack.matches(fill.output(), menu.getSlot(widget.resultSlot()).getItem())) throw new ChangedResult();
                } catch (RuntimeException | Error failure) {
                    widget.consumeIngredients(() -> {
                        for (int i = 0; i < inventory.size(); i++) inventory.set(i, oldInventory.get(i));
                        for (int i = 0; i < slots.size(); i++) slots.get(i).set(oldGrid.get(i));
                    });
                    throw failure;
                }
            });
            committed = true;
            var session = TerminalSession.of(menu);
            for (ItemStack stack : gridRemainder)
                if (!stack.isEmpty()) session.displaced.addLast(stack);
            session.gridChanged = true;
            session.emcChanged |= current.total().signum() > 0;
            if (!session.taking) finish(menu, player, session);
            return true;
        } catch (ChangedResult changed) {
            return false;
        } finally {
            if (!committed) {
                for (KeyAmount value : withdrawn) {
                    KeyAmount rest = storage.insert(value.key(), value.amount(), false);
                    if (!rest.isEmpty() && rest.key() instanceof ItemStackKey key)
                        player.getInventory().placeItemBackInInventory(key.copyStackWithCount(Math.toIntExact(rest.amount())));
                }
            }
        }
    }

    /** Keep displaced ingredients out of the output's destination until the native click has put the product away. */
    public static <T> T duringTake(DimensionsNetMenu menu, ServerPlayer player, Supplier<T> action) {
        var session = TerminalSession.of(menu);
        if (session.taking) return action.get();
        session.taking = true;
        try {
            return action.get();
        } finally {
            session.taking = false;
            finish(menu, player, session);
        }
    }

    public static void returnDisplaced(DimensionsNetMenu menu, ServerPlayer player) {
        var items = TerminalSession.of(menu).displaced;
        if (items.isEmpty()) return;
        boolean toStorage = PlayerWorkbenches.of(player).returnToStorage();
        var storage = menu.crafting().storage();
        while (!items.isEmpty()) CraftingWidget.returnStack(player, storage, items.removeFirst(), toStorage);
    }

    private static void finish(DimensionsNetMenu menu, ServerPlayer player, TerminalSession session) {
        if (!session.gridChanged) return;
        returnDisplaced(menu, player);
        if (session.emcChanged) {
            var account = Util.getKnowledgeProvider(player);
            if (account != null) account.syncEmc(player);
        }
        session.gridChanged = false;
        session.emcChanged = false;
        player.getInventory().setChanged();
        menu.broadcastChanges();
    }

    public static @Nullable Fill repeat(DimensionsNetMenu menu, ServerPlayer player) {
        TerminalSession session = TerminalSession.of(menu);
        if (!active(menu, player) || session.lastRecipe == null || !menu.stillValid(player)
                || !ItemStack.listMatches(session.afterTake, menu.crafting().inputSlots().stream().map(Slot::getItem).toList())) return null;
        return plan(menu, player, session.lastRecipe, 1);
    }
}
