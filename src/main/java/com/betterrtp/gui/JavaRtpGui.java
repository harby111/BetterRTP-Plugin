package com.betterrtp.gui;

import com.betterrtp.config.ConfigManager;
import com.betterrtp.config.Messages;
import com.betterrtp.config.RtpConfig;
import com.betterrtp.rtp.Destination;
import com.betterrtp.rtp.RtpService;
import com.betterrtp.rtp.WorldResolver;
import com.betterrtp.util.Parsers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 27-slot chest menu with exactly three destinations. Built on open; no tasks, no animation. */
public final class JavaRtpGui {
    private static final int SIZE = 27;
    private final RtpService service;
    private final ConfigManager cm;

    public JavaRtpGui(RtpService service, ConfigManager cm) {
        this.service = service;
        this.cm = cm;
    }

    public void open(Player p) {
        RtpConfig cfg = cm.config();
        Messages msgs = cm.messages();
        RtpGuiHolder holder = new RtpGuiHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, msgs.parse(cfg.gui().title()));
        holder.attach(inv);

        ItemStack filler = item(Parsers.material(cfg.gui().fillerMaterial()).orElse(Material.GRAY_STAINED_GLASS_PANE),
                Component.text(" "), List.of());
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        for (Destination d : Destination.values()) {
            RtpConfig.GuiButton slotCfg = cfg.gui().buttons().get(d);
            Optional<WorldResolver.Resolved> resolved = service.resolver().resolve(d, cfg);
            RtpConfig.GuiButton b = resolved.isPresent() ? slotCfg : cfg.gui().unavailable();
            List<TagResolver> ph = new ArrayList<>();
            ph.add(Messages.p("destination", cfg.destinationNames().get(d)));
            resolved.ifPresent(r -> {
                ph.add(Messages.p("world", r.world().getName()));
                ph.add(Messages.p("radius", r.settings().radius()));
            });
            TagResolver[] arr = ph.toArray(new TagResolver[0]);
            List<Component> lore = new ArrayList<>();
            for (String line : b.lore()) lore.add(msgs.parse(line, arr));
            Material mat = Parsers.material(b.material()).orElse(resolved.isPresent() ? Material.PAPER : Material.BARRIER);
            inv.setItem(slotCfg.slot(), item(mat, msgs.parse(b.name(), arr), lore));
            holder.map(slotCfg.slot(), d);
        }
        p.openInventory(inv);
    }

    private static ItemStack item(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(c -> c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)).toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return it;
    }
}
