package com.wjx.neoforge.projectexa.util;

import com.wjx.neoforge.projectexa.Main;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import org.apache.maven.artifact.versioning.ComparableVersion;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientUpdateChecker {
    private static final String VERSION_URL = "https://raw.giteeusercontent.com/wjx4r/FML_GERB_NetData/raw/master/OtherMODUpdate/ProjectExa.txt";
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile @Nullable String availableVersion;
    // Client-thread state intentionally survives disconnects and world changes.
    private static boolean notificationShown;

    private ClientUpdateChecker() {
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) return;
        String currentVersion = ModList.get().getModContainerById(Main.MOD_ID)
                .orElseThrow().getModInfo().getVersion().toString();
        Thread worker = new Thread(() -> checkForUpdate(currentVersion), "ProjectExA-UpdateChecker");
        worker.setDaemon(true);
        worker.start();
    }

    private static void checkForUpdate(String currentVersion) {
        try {
            ComparableVersion installed = new ComparableVersion(normalizeVersion(currentVersion));
            HttpURLConnection connection = (HttpURLConnection) new URL(VERSION_URL).openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            connection.setUseCaches(false);
            try {
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Update server returned HTTP " + connection.getResponseCode());
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    reader.readLine(); // The first line belongs to Minecraft 1.20.1.
                    String secondLine = reader.readLine();
                    if (secondLine == null) throw new IOException("Update server did not provide a second version line");
                    String remoteVersion = secondLine.replace("\uFEFF", "").trim();
                    ComparableVersion latest = new ComparableVersion(normalizeVersion(remoteVersion));
                    Main.Logger.info("ProjectExA update check: installed {}, latest {}", installed, latest);
                    if (latest.compareTo(installed) > 0) {
                        availableVersion = remoteVersion;
                    }
                }
            } finally {
                connection.disconnect();
            }
        } catch (IOException | IllegalArgumentException exception) {
            Main.Logger.warn("Unable to check for ProjectExA updates", exception);
        }
    }

    private static String normalizeVersion(String version) {
        String normalized = version.trim();
        // The jar version includes Minecraft's version; the remote file uses the mod version.
        String minecraftPrefix = SharedConstants.getCurrentVersion().getName() + "-";
        if (normalized.startsWith(minecraftPrefix)) {
            normalized = normalized.substring(minecraftPrefix.length());
        }
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() > 128 || !normalized.matches("[0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?")) {
            throw new IllegalArgumentException("Invalid mod version: " + version);
        }
        return normalized;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (notificationShown) return;
        String version = availableVersion;
        if (version == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        // Wait until the world loading screen closes, including when the HTTP result arrives late.
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        minecraft.player.displayClientMessage(Component.translatable("text.projectexa.update_available", version)
                .withStyle(ChatFormatting.YELLOW), false);
        notificationShown = true;
    }
}
