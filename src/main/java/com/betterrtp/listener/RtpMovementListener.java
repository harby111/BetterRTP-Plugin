package com.betterrtp.listener;

import com.betterrtp.config.ConfigManager;
import com.betterrtp.config.Msg;
import com.betterrtp.rtp.RtpRequest;
import com.betterrtp.rtp.RtpService;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Registered ONLY while at least one RTP request is active (see RtpService), so an idle server
 * pays nothing for PlayerMoveEvent.
 */
public final class RtpMovementListener implements Listener {
    private static final double HORIZONTAL_SQ = 0.2 * 0.2;
    private static final double VERTICAL = 0.5;
    private final RtpService service;
    private final ConfigManager cm;

    public RtpMovementListener(RtpService service, ConfigManager cm) {
        this.service = service;
        this.cm = cm;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) return;           // head rotation only
        RtpRequest r = service.activeFor(e.getPlayer().getUniqueId());
        if (r == null || !r.cancellable() || !cm.config().cancelOnMove()) return;
        Location to = e.getTo();
        if (!to.getWorld().getUID().equals(r.startWorld())) {
            service.cancel(r.playerId(), Msg.CANCELLED_WORLD);
            return;
        }
        double dx = to.getX() - r.startX(), dz = to.getZ() - r.startZ(), dy = to.getY() - r.startY();
        if (dx * dx + dz * dz > HORIZONTAL_SQ || Math.abs(dy) > VERTICAL) {
            service.cancel(r.playerId(), Msg.CANCELLED_MOVE);
        }
    }
}
