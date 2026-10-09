package com.betterrtp.effect;

import com.betterrtp.config.ConfigLoader;
import com.betterrtp.config.Messages;
import com.betterrtp.config.Msg;
import com.betterrtp.config.RtpConfig;
import com.betterrtp.rtp.RtpRequest;
import com.betterrtp.util.Parsers;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Countdown display (actionbar/title/bossbar), sounds and particles. Built once per config load. */
public final class RtpEffects {
    private final RtpConfig cfg;
    private final Messages messages;
    private final Sound teleportSound;
    private final Sound countdownSound;
    private final Particle countdownParticle;
    private final Particle teleportParticle;
    private final BossBar.Color barColor;
    private final BossBar.Overlay barOverlay;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public RtpEffects(RtpConfig cfg, Messages messages) {
        this.cfg = cfg;
        this.messages = messages;
        this.teleportSound = Parsers.sound(cfg.teleportSound().name()).orElse(null);
        this.countdownSound = Parsers.sound(cfg.countdownSound().name()).orElse(null);
        this.countdownParticle = Parsers.particle(cfg.countdownParticles().name()).orElse(null);
        this.teleportParticle = Parsers.particle(cfg.teleportParticles().name()).orElse(null);
        BossBar.Color c = ConfigLoader.bossColor(cfg.display().bossbarColor());
        BossBar.Overlay o = ConfigLoader.bossOverlay(cfg.display().bossbarStyle());
        this.barColor = c != null ? c : BossBar.Color.GREEN;
        this.barOverlay = o != null ? o : BossBar.Overlay.NOTCHED_10;
    }

    /** Shown once per countdown second. */
    public void showCountdown(Player p, RtpRequest r) {
        TagResolver[] ph = {Messages.p("seconds", r.remaining()), Messages.p("destination", r.label())};
        RtpConfig.Display d = cfg.display();
        if (d.actionbar() && messages.has(Msg.COUNTDOWN_ACTIONBAR)) p.sendActionBar(messages.render(Msg.COUNTDOWN_ACTIONBAR, ph));
        if (d.title()) {
            p.showTitle(Title.title(messages.render(Msg.COUNTDOWN_TITLE, ph), messages.render(Msg.COUNTDOWN_SUBTITLE, ph),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(100))));
        }
        if (d.bossbar()) {
            Component name = messages.render(Msg.COUNTDOWN_BOSSBAR, ph);
            float progress = Math.max(0f, Math.min(1f, (float) r.remaining() / r.totalSeconds()));
            BossBar bar = bars.get(p.getUniqueId());
            if (bar == null) {
                bar = BossBar.bossBar(name, progress, barColor, barOverlay);
                bars.put(p.getUniqueId(), bar);
                p.showBossBar(bar);
            } else {
                bar.name(name).progress(progress);
            }
        }
        RtpConfig.SoundCfg s = cfg.countdownSound();
        if (s.enabled() && countdownSound != null) p.playSound(p.getLocation(), countdownSound, s.volume(), s.pitch());
        RtpConfig.ParticleCfg pc = cfg.countdownParticles();
        if (pc.enabled() && countdownParticle != null && pc.count() > 0) spawn(p.getLocation(), countdownParticle, pc);
    }

    /** Removes countdown visuals. Safe to call repeatedly or for offline players. */
    public void clear(UUID id) {
        BossBar bar = bars.remove(id);
        if (bar != null) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(bar);
        }
    }

    public void clearActionbar(Player p) {
        p.sendActionBar(Component.empty());
    }

    public void playTeleport(Player p) {
        RtpConfig.SoundCfg s = cfg.teleportSound();
        Location loc = p.getLocation();
        if (s.enabled() && teleportSound != null) p.getWorld().playSound(loc, teleportSound, s.volume(), s.pitch());
        RtpConfig.ParticleCfg pc = cfg.teleportParticles();
        if (pc.enabled() && teleportParticle != null && pc.count() > 0) spawn(loc, teleportParticle, pc);
    }

    public void shutdown() {
        for (UUID id : java.util.List.copyOf(bars.keySet())) clear(id);
    }

    private static void spawn(Location base, Particle particle, RtpConfig.ParticleCfg pc) {
        World w = base.getWorld();
        if (w == null) return;
        if (pc.radius() > 0) {
            for (int i = 0; i < pc.count(); i++) {
                double angle = 2 * Math.PI * i / pc.count();
                double y = base.getY() + 0.1 + (1.8 * i / pc.count());
                w.spawnParticle(particle, base.getX() + Math.cos(angle) * pc.radius(), y,
                        base.getZ() + Math.sin(angle) * pc.radius(), 1, pc.spread(), pc.spread(), pc.spread(), 0.0);
            }
        } else {
            w.spawnParticle(particle, base.getX(), base.getY() + 1.0, base.getZ(), pc.count(),
                    pc.spread(), pc.spread(), pc.spread(), 0.0);
        }
    }
}
