package com.betterrtp.gui;

import com.betterrtp.rtp.Destination;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Marks BetterRTP menus and maps slots to destinations. Holds no player or world references. */
public final class RtpGuiHolder implements InventoryHolder {
    private final Map<Integer, Destination> slots = new HashMap<>();
    private Inventory inventory;
    private boolean used;

    void attach(Inventory inventory) { this.inventory = inventory; }
    void map(int slot, Destination d) { slots.put(slot, d); }

    public Destination destinationAt(int slot) { return slots.get(slot); }

    /** First caller wins: guards against double-clicks and click spam. */
    public boolean markUsed() {
        if (used) return false;
        used = true;
        return true;
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
