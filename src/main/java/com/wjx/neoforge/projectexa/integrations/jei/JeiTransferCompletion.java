package com.wjx.neoforge.projectexa.integrations.jei;

import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;

/** Client-only state for our asynchronous crafting transfers; contains no server-side JEI dependency. */
public final class JeiTransferCompletion {
    private static final long TIMEOUT_NANOS = 30_000_000_000L;
    private static final int MAX_PENDING = 64;
    private static final Map<Integer, Pending> PENDING = new LinkedHashMap<>();
    private static int nextId;

    private record Pending(IRecipeTransferContext<?, ?> context, long startedAt) {}

    private JeiTransferCompletion() {}

    public static @Nullable IRecipeTransferError transfer(IRecipeTransferContext<?, ?> context, boolean doTransfer,
                                                         IntFunction<@Nullable IRecipeTransferError> action) {
        if (!doTransfer) return action.apply(-1);
        tick();
        while (PENDING.size() >= MAX_PENDING) reject(PENDING.keySet().iterator().next());
        int id;
        do {
            id = nextId;
            nextId = (nextId + 1) & Integer.MAX_VALUE;
        } while (PENDING.containsKey(id));
        PENDING.put(id, new Pending(context, System.nanoTime()));
        try {
            IRecipeTransferError error = action.apply(id);
            if (error != null) reject(id);
            return error;
        } catch (RuntimeException exception) {
            reject(id);
            throw exception;
        }
    }

    public static void complete(int transferId, int containerId, boolean success) {
        Pending pending = PENDING.remove(transferId);
        if (pending == null) return;
        var player = Minecraft.getInstance().player;
        boolean accepted = success && player != null && player == pending.context().getPlayer()
                && player.containerMenu.containerId == containerId
                && System.nanoTime() - pending.startedAt() < TIMEOUT_NANOS;
        pending.context().completeRecipeTransfer(accepted ? RecipeTransferResult.SUCCESS : RecipeTransferResult.REJECTED);
    }

    public static void tick() {
        var player = Minecraft.getInstance().player;
        long now = System.nanoTime();
        var expired = PENDING.entrySet().stream()
                .filter(entry -> entry.getValue().context().getPlayer() != player
                        || now - entry.getValue().startedAt() >= TIMEOUT_NANOS)
                .map(Map.Entry::getKey).toList();
        expired.forEach(JeiTransferCompletion::reject);
    }

    public static void clear() {
        new ArrayList<>(PENDING.keySet()).forEach(JeiTransferCompletion::reject);
    }

    private static void reject(int id) {
        Pending pending = PENDING.remove(id);
        if (pending != null) pending.context().completeRecipeTransfer(RecipeTransferResult.REJECTED);
    }
}
