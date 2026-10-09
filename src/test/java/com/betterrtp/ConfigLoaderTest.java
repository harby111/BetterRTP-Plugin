package com.betterrtp;

import com.betterrtp.config.ConfigLoader;
import com.betterrtp.config.NameChecks;
import com.betterrtp.config.RtpConfig;
import com.betterrtp.config.WorldRtpSettings;
import com.betterrtp.rtp.Destination;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {
    private final List<String> warnings = new ArrayList<>();

    private RtpConfig load(String yaml) {
        var y = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        return new ConfigLoader(warnings::add, NameChecks.lenient()).load(y);
    }

    @Test void emptyConfigGivesSafeDefaults() {
        RtpConfig c = load("");
        assertEquals("world", c.defaultWorld());
        assertEquals(30, c.cooldownSeconds());
        assertEquals(5, c.countdownSeconds());
        assertEquals(3000, c.globalDefaults().radius());
        assertEquals(64, c.globalDefaults().minY());
        assertEquals(320, c.globalDefaults().maxY());
        assertEquals(50, c.safeAttempts());
        assertEquals(20, c.searchTimeoutSeconds());
        assertFalse(c.cache().enabled());
        assertEquals(25, c.cache().size());
        assertEquals(3, c.biome().attemptsMultiplier());
        assertTrue(c.blacklist().contains("BEDROCK") && c.blacklist().contains("OAK_LEAVES"));
        assertEquals(3, c.gui().buttons().size());
        assertEquals(11, c.gui().buttons().get(Destination.OVERWORLD).slot());
        assertEquals(3, c.bedrockGui().buttons().size());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test void invalidNumbersFallBackWithWarnings() {
        RtpConfig c = load("""
                radius: 0
                countdown-time: 0
                cooldown-time: -5
                safe-location-attempts: 0
                teleport-max-search-seconds: 0
                location-cache: { size: -1 }
                """);
        assertEquals(3000, c.globalDefaults().radius());
        assertEquals(5, c.countdownSeconds());
        assertEquals(30, c.cooldownSeconds());
        assertEquals(50, c.safeAttempts());
        assertEquals(20, c.searchTimeoutSeconds());
        assertEquals(25, c.cache().size());
        assertTrue(warnings.size() >= 6, warnings.toString());
    }

    @Test void minYMustBeBelowMaxY() {
        RtpConfig c = load("min-y: 200\nmax-y: 100\nworlds:\n  w:\n    min-y: 90\n    max-y: 90\n");
        assertEquals(64, c.globalDefaults().minY());
        assertEquals(320, c.globalDefaults().maxY());
        assertEquals(64, c.worlds().get("w").minY());
        assertEquals(2, warnings.size());
    }

    @Test void unsupportedShapeWarnsAndUsesSquare() {
        RtpConfig c = load("shape: CIRCLE\nworlds:\n  w:\n    shape: HEXAGON\n");
        assertEquals("SQUARE", c.globalDefaults().shape());
        assertEquals("SQUARE", c.worlds().get("w").shape());
        assertEquals(2, warnings.size());
    }

    @Test void perWorldOverridesCenterAndCooldown() {
        RtpConfig c = load("""
                cooldown-time: 30
                radius: 3000
                worlds:
                  world:
                    radius: 500
                    cooldown-time: 10
                    center: { x: 100, z: -50 }
                  world_nether:
                    enabled: false
                """);
        WorldRtpSettings w = c.worlds().get("world");
        assertEquals(500, w.radius());
        assertEquals(10, w.cooldownSeconds());
        assertTrue(w.hasCenter());
        assertEquals(100, w.centerX());
        assertEquals(-50, w.centerZ());
        WorldRtpSettings n = c.worlds().get("world_nether");
        assertFalse(n.enabled());
        assertEquals(3000, n.radius());
        assertEquals(30, n.cooldownSeconds());
        assertFalse(n.hasCenter());
        assertEquals(List.of("world", "world_nether"), List.copyOf(c.worlds().keySet()), "config order kept");
    }

    @Test void biomeMultiplierAndParticleCountAreClamped() {
        RtpConfig c = load("biome-rtp: { attempts-multiplier: 9999 }\nparticles:\n  teleport: { count: 100000 }\n");
        assertEquals(10, c.biome().attemptsMultiplier());
        assertEquals(100, c.teleportParticles().count());
    }

    @Test void unknownNamesAreRejected() {
        RtpConfig c = load("""
                blacklisted-blocks: [BEDROCK, INVALID_BLOCK]
                teleport-sound: { sound-type: INVALID_SOUND, volume: 99, pitch: 5 }
                particles: { countdown: { type: INVALID_PARTICLE } }
                countdown-display: { bossbar-color: NOPE, bossbar-style: NOPE }
                """);
        assertEquals(java.util.Set.of("BEDROCK"), c.blacklist());
        assertEquals("ENTITY_ENDERMAN_TELEPORT", c.teleportSound().name());
        assertEquals(1.0f, c.teleportSound().volume());
        assertEquals(1.0f, c.teleportSound().pitch());
        assertEquals("PORTAL", c.countdownParticles().name());
        assertEquals("GREEN", c.display().bossbarColor());
        assertEquals("SEGMENTED_10", c.display().bossbarStyle());
        assertEquals(7, warnings.size(), warnings.toString());
    }

    @Test void invalidOrDuplicateGuiSlotsGetDistinctValidSlots() {
        RtpConfig c = load("gui:\n  items:\n    overworld: { slot: 13 }\n    nether: { slot: 99 }\n");
        var slots = new java.util.HashSet<Integer>();
        for (Destination d : Destination.values()) {
            int s = c.gui().buttons().get(d).slot();
            assertTrue(s >= 0 && s <= 26);
            slots.add(s);
        }
        assertEquals(3, slots.size());
        assertEquals(13, c.gui().buttons().get(Destination.OVERWORLD).slot());
        assertFalse(warnings.isEmpty());
    }

    @Test void blacklistExplicitlyEmptyIsRespected() {
        RtpConfig c = load("blacklisted-blocks: []\n");
        assertTrue(c.blacklist().isEmpty());
    }
}
