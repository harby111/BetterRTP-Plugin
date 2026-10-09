package com.betterrtp.gui;

import com.betterrtp.BetterRTPPlugin;
import com.betterrtp.config.ConfigManager;
import com.betterrtp.config.Messages;
import com.betterrtp.config.Msg;
import com.betterrtp.config.RtpConfig;
import com.betterrtp.rtp.Destination;
import com.betterrtp.rtp.RtpService;
import com.betterrtp.util.Threads;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Native Bedrock SimpleForm with exactly three buttons (Overworld, Nether, End). */
public final class BedrockRtpGui {
    private final BetterRTPPlugin plugin;
    private final RtpService service;
    private final ConfigManager cm;

    public BedrockRtpGui(BetterRTPPlugin plugin, RtpService service, ConfigManager cm) {
        this.plugin = plugin;
        this.service = service;
        this.cm = cm;
    }

    /** @return true if the form was sent. */
    public boolean open(Player p) {
        RtpConfig cfg = cm.config();
        Messages msgs = cm.messages();
        List<BedrockSupport.Button> buttons = new ArrayList<>(3);
        for (Destination d : Destination.values()) {
            RtpConfig.BedrockButton b = cfg.bedrockGui().buttons().get(d);
            buttons.add(new BedrockSupport.Button(msgs.legacy(b.text()), b.imageType(), b.imageData()));
        }
        var menu = new BedrockSupport.Menu(msgs.legacy(cfg.bedrockGui().title()), msgs.legacy(cfg.bedrockGui().content()), buttons);
        UUID id = p.getUniqueId();
        try {
            return plugin.bedrock().sendMenu(id, menu, index ->
                    Threads.main(plugin, () -> {
                        Player online = Bukkit.getPlayer(id);   // never keep the Player across the async gap
                        if (online == null) return;
                        Destination[] all = Destination.values();
                        if (index < 0 || index >= all.length) return;
                        Destination d = all[index];
                        if (!cm.config().bedrockGui().buttons().get(d).enabled()) {
                            msgs.send(online, Msg.DESTINATION_UNAVAILABLE, Messages.p("destination", cm.config().destinationNames().get(d)));
                            return;
                        }
                        service.startDestination(online, d);
                    }));
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning("Could not send Bedrock form: " + e);
            return false;
        }
    }
}
