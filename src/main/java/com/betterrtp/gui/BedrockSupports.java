package com.betterrtp.gui;

import com.betterrtp.BetterRTPPlugin;
import com.betterrtp.gui.floodgate.FloodgateBedrockSupport;
import org.bukkit.Bukkit;

/** Detects Floodgate once at startup. Any linkage problem degrades to "no Bedrock support". */
public final class BedrockSupports {
    private BedrockSupports() { }

    public static BedrockSupport detect(BetterRTPPlugin plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            plugin.getLogger().info("Floodgate not found: Bedrock players will use the standard inventory menu.");
            return BedrockSupport.NONE;
        }
        try {
            BedrockSupport s = new FloodgateBedrockSupport();
            plugin.getLogger().info("Floodgate detected: Bedrock players get a native SimpleForm menu.");
            return s;
        } catch (Throwable t) {   // NoClassDefFoundError etc.: never break startup
            plugin.getLogger().warning("Floodgate is present but its API could not be used (" + t + "). Bedrock menu disabled.");
            return BedrockSupport.NONE;
        }
    }
}
