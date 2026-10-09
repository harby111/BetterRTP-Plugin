package com.betterrtp.config;

import com.betterrtp.rtp.Destination;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fully validated, immutable configuration snapshot. */
public record RtpConfig(
        String defaultWorld,
        int cooldownSeconds,
        int countdownSeconds,
        boolean cancelOnMove,
        int safeAttempts,
        int searchTimeoutSeconds,
        boolean preloadChunks,
        boolean rtpguiCommandEnabled,
        boolean openOnRtp,
        boolean debug,
        WorldRtpSettings globalDefaults,
        Map<String, WorldRtpSettings> worlds,
        Cache cache,
        BiomeRtp biome,
        Set<String> blacklist,
        SoundCfg teleportSound,
        SoundCfg countdownSound,
        Display display,
        ParticleCfg countdownParticles,
        ParticleCfg teleportParticles,
        Safety safety,
        Performance performance,
        Map<Destination, String> destinationNames,
        Gui gui,
        BedrockGui bedrockGui) {

    public record Cache(boolean enabled, int size, int refillIntervalTicks) { }
    public record BiomeRtp(boolean enabled, boolean generateChunks, int attemptsMultiplier) { }
    public record SoundCfg(boolean enabled, String name, float volume, float pitch) { }
    public record ParticleCfg(boolean enabled, String name, int count, double radius, double spread) { }
    public record Display(boolean actionbar, boolean title, boolean bossbar, String bossbarColor, String bossbarStyle) { }
    public record Safety(boolean rejectWater, boolean rejectLava, boolean rejectFire, boolean rejectHazards,
                         boolean rejectUnderground, int verticalClearance, boolean ignoreLeaves,
                         int maxScanHeight, boolean netherEnabled, int netherMinimumOpenHeight) { }
    public record Performance(int maxGlobalSearches, int maxQueueSize, int chunkLoadTimeoutMs, int maxCandidatesPerSecond,
                              boolean searchDuringCountdown, int countdownSearchSlowdown) { }

    public record GuiButton(int slot, String material, String name, List<String> lore) { }
    public record Gui(String title, String fillerMaterial, Map<Destination, GuiButton> buttons, GuiButton unavailable) { }

    public record BedrockButton(boolean enabled, String text, String imageType, String imageData) { }
    public record BedrockGui(boolean enabled, String title, String content, Map<Destination, BedrockButton> buttons) { }
}