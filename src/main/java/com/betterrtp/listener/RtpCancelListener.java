package com.betterrtp.listener;

import com.betterrtp.config.Msg;
import com.betterrtp.rtp.RtpRequest;
import com.betterrtp.rtp.RtpService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** Rare, cheap events: damage, world change, teleport by others, death, quit, world unload. */
public final class RtpCancelListener implements Listener {
    private final RtpService service;

    public RtpCancelListener(RtpService service) { this.service = service; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) service.cancel(p.getUniqueId(), Msg.CANCELLED_DAMAGE);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        service.cancel(e.getPlayer().getUniqueId(), Msg.CANCELLED_WORLD);   // ignored while our own teleport is in flight
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        RtpRequest r = service.activeFor(e.getPlayer().getUniqueId());
        if (r != null && r.cancellable()) service.cancel(r.playerId(), Msg.CANCELLED_MOVE);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) { service.abort(e.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        service.abort(e.getPlayer().getUniqueId());
        service.cooldowns().onQuit(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent e) {
        if (!e.isCancelled()) service.onWorldUnload(e.getWorld());
    }
}
