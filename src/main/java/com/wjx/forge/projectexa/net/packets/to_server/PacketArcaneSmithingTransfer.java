package com.wjx.forge.projectexa.net.packets.to_server;

import com.wjx.forge.projectexa.gui.container.ContainerArcaneTransmutationTablet;
import com.wjx.forge.projectexa.item.ItemArcaneTransmutationTablet;
import com.wjx.forge.projectexa.net.packets.IPacket;
import com.wjx.forge.projectexa.util.Util;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;

public record PacketArcaneSmithingTransfer(ResourceLocation recipeId, List<List<ItemStack>> choices,
                                           boolean transferAll) implements IPacket {
    @Override public void handle(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null || !PacketOpenArcaneTransmutationTablet.hasTablet(player)
                || !PacketArcaneTransmutationTabletRecipeTransfer.validRecipe(choices)) return;
        IKnowledgeProvider provider = Util.getKnowledgeProvider(player);
        if (provider == null || !provider.hasKnowledge(new ItemStack(Items.SMITHING_TABLE))) return;
        var recipe = player.level().getRecipeManager().byKey(recipeId).orElse(null);
        if (!(recipe instanceof SmithingRecipe smithingRecipe)) return;
        if (!(player.containerMenu instanceof ContainerArcaneTransmutationTablet)) {
            ItemArcaneTransmutationTablet.openFromInventory(player);
        }
        if (player.containerMenu instanceof ContainerArcaneTransmutationTablet menu
                && menu.getPlayer() == player && menu.stillValid(player)) {
            menu.transferSmithingRecipe(smithingRecipe, choices, transferAll);
        }
    }

    @Override public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(recipeId);
        PacketArcaneTransmutationTabletRecipeTransfer.writeRecipe(buffer, choices);
        buffer.writeBoolean(transferAll);
    }

    public static PacketArcaneSmithingTransfer decode(FriendlyByteBuf buffer) {
        return new PacketArcaneSmithingTransfer(buffer.readResourceLocation(),
                PacketArcaneTransmutationTabletRecipeTransfer.readRecipe(buffer), buffer.readBoolean());
    }
}
