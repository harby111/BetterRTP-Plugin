package com.betterrtp;

import com.betterrtp.command.RtpCommandRegistrar;
import com.betterrtp.config.ConfigManager;
import com.betterrtp.gui.BedrockSupport;
import com.betterrtp.gui.BedrockSupports;
import com.betterrtp.listener.InventoryListener;
import com.betterrtp.listener.RtpCancelListener;
import com.betterrtp.rtp.RtpService;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetterRTPPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private RtpService service;
    private BedrockSupport bedrock = BedrockSupport.NONE;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();
        bedrock = BedrockSupports.detect(this);
        service = new RtpService(this, configManager);
        getServer().getPluginManager().registerEvents(new InventoryListener(this, service), this);
        getServer().getPluginManager().registerEvents(new RtpCancelListener(service), this);
        RtpCommandRegistrar.register(this, service);
        getLogger().info("BetterRTP " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (service != null) service.shutdown();
        getLogger().info("BetterRTP disabled.");
    }

    public BedrockSupport bedrock() { return bedrock; }

    /** Reloads config.yml and rebuilds derived state. On failure the previous configuration stays active. */
    public boolean reloadAll() {
        boolean ok = configManager.reload();
        if (ok) service.applyConfig();
        getLogger().info(ok ? "Configuration reloaded." : "Configuration reload failed; keeping the previous configuration.");
        return ok;
    }
}
