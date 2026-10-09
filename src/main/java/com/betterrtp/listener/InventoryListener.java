package com.betterrtp.listener;

import com.betterrtp.BetterRTPPlugin;
import com.betterrtp.config.Messages;
import com.betterrtp.config.Msg;
import com.betterrtp.gui.RtpGuiHolder;
import com.betterrtp.rtp.Destination;
import com.betterrtp.rtp.RtpService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/** Makes the menu read-only and routes clicks to the service. */
public final class InventoryListener implements Listener {
    private final BetterRTPPlugin plugin;
    private final RtpService service;

    public InventoryListener(BetterRTPPlugin plugin, RtpService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof RtpGuiHolder holder)) return;
        e.setCancelled(true);                                    // no taking, moving, shift-click, number keys...
        if (!top.equals(e.getClickedInventory())) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;
        Destination d = holder.destinationAt(e.getSlot());
        if (d == null || !e.getClick().isLeftClick() && !e.getClick().isRightClick()) return;
        if (!holder.markUsed()) return;                          // double-click / click spam guard
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {            // close + start outside the click event
            Player p = Bukkit.getPlayer(id);
            if (p == null) return;
            p.closeInventory();
            service.startDestination(p, d);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof RtpGuiHolder) e.setCancelled(true);
    }
}
