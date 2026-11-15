package com.wjx.neoforge.projectexa.util;

import com.wjx.neoforge.projectexa.Main;
import com.wjx.neoforge.projectexa.capability.CapabilityAlchemicalBookLocations.AlchemicalBookLocationData;
import com.wjx.neoforge.projectexa.capability.CapabilityAlchemicalBookLocations.TeleportLocation;
import com.wjx.neoforge.projectexa.item.ItemAlchemicalBook;
import com.wjx.neoforge.projectexa.registries.AttachmentTypes;
import com.wjx.neoforge.projectexa.registries.DataComponentTypes;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.ModifyRegistriesEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Decode old 1.21.1 data as distinct types so old and current locations can be merged safely. */
public final class LegacyAlchemicalBookMigration {
    private static final String LEGACY_MOD_ID = "projectexpansion";
    private static final DeferredRegister<AttachmentType<?>> LEGACY_ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LEGACY_MOD_ID);
    private static final DeferredRegister.DataComponents LEGACY_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LEGACY_MOD_ID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<AlchemicalBookLocationData>> PLAYER_LOCATIONS =
            LEGACY_ATTACHMENTS.register("alchemical_book_locations", () -> AttachmentType.builder(AlchemicalBookLocationData::new)
                    .serialize(AlchemicalBookLocationData.CODEC)
                    .copyHandler(AlchemicalBookLocationData::copy)
                    .copyOnDeath()
                    .build());
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<AlchemicalBookLocationData>> BOOK_LOCATIONS =
            LEGACY_COMPONENTS.registerComponentType("alchemical_book_locations", builder -> builder
                    .persistent(AlchemicalBookLocationData.CODEC)
                    .networkSynchronized(AlchemicalBookLocationData.STREAM_CODEC));
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<DataComponentTypes.OwnerData>> BOOK_OWNER =
            LEGACY_COMPONENTS.registerComponentType("uuid", builder -> builder
                    .persistent(DataComponentTypes.OwnerData.CODEC)
                    .networkSynchronized(DataComponentTypes.OwnerData.STREAM_CODEC));
    private static boolean enabled;

    private LegacyAlchemicalBookMigration() {}

    public static void register(IEventBus modEventBus) {
        // When both mods are installed, the original mod owns its IDs and data.
        if (ModList.get().isLoaded(LEGACY_MOD_ID)) return;
        LEGACY_ATTACHMENTS.register(modEventBus);
        LEGACY_COMPONENTS.register(modEventBus);
        modEventBus.addListener(LegacyAlchemicalBookMigration::registerAliases);
        NeoForge.EVENT_BUS.addListener(LegacyAlchemicalBookMigration::onPlayerLoggedIn);
        enabled = true;
    }

    private static void registerAliases(ModifyRegistriesEvent event) {
        var items = event.getRegistry(Registries.ITEM);
        for (String tier : List.of("basic", "advanced", "master", "arcane")) {
            String path = tier + "_alchemical_book";
            items.addAlias(ResourceLocation.fromNamespaceAndPath(LEGACY_MOD_ID, path), Main.rl(path));
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            migratePlayer(player);
        }
    }

    public static void migratePlayer(ServerPlayer player) {
        if (!enabled || !player.hasData(PLAYER_LOCATIONS)) return;
        AlchemicalBookLocationData current = player.getData(AttachmentTypes.ALCHEMICAL_BOOK_LOCATIONS);
        player.setData(AttachmentTypes.ALCHEMICAL_BOOK_LOCATIONS, merge(current, player.getData(PLAYER_LOCATIONS)));
        player.removeData(PLAYER_LOCATIONS);
    }

    public static void migrateBook(ItemStack stack) {
        if (!enabled || !(stack.getItem() instanceof ItemAlchemicalBook)) return;
        AlchemicalBookLocationData legacy = stack.get(BOOK_LOCATIONS);
        if (legacy != null) {
            AlchemicalBookLocationData current = stack.getOrDefault(DataComponentTypes.ALCHEMICAL_BOOK_LOCATIONS, new AlchemicalBookLocationData());
            stack.set(DataComponentTypes.ALCHEMICAL_BOOK_LOCATIONS, merge(current, legacy));
            stack.remove(BOOK_LOCATIONS);
        }
        DataComponentTypes.OwnerData owner = stack.get(BOOK_OWNER);
        if (owner != null) {
            if (!stack.has(DataComponentTypes.OWNER)) {
                stack.set(DataComponentTypes.OWNER, owner);
            }
            stack.remove(BOOK_OWNER);
        }
    }

    private static AlchemicalBookLocationData merge(AlchemicalBookLocationData current, AlchemicalBookLocationData legacy) {
        var locations = new ArrayList<>(current.getLocations());
        var names = new HashSet<String>();
        int nextIndex = 1;
        for (var location : locations) {
            names.add(location.name());
            if (!location.isBack()) nextIndex = Math.max(nextIndex, location.index() + 1);
        }
        // Sort ties by name for deterministic ordering, without changing existing Arcanum indices.
        var additions = legacy.getLocations().stream()
                .sorted(Comparator.comparingInt(TeleportLocation::index).thenComparing(TeleportLocation::name))
                .toList();
        for (var location : additions) {
            if (names.add(location.name())) {
                locations.add(location.setIndex(location.isBack() ? 0 : nextIndex++));
            }
        }
        return current.setLocations(locations);
    }
}
