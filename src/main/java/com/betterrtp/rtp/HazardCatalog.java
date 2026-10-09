package com.betterrtp.rtp;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Data-driven list of dangerous block types, keyed by Material name so it needs no server to test.
 * To add hazards later, register more names; the RTP engine does not change.
 */
public final class HazardCatalog {
    public enum Category { WATER, LAVA, FIRE, OTHER }

    private final Map<String, Category> byName = new HashMap<>();

    public HazardCatalog() {
        register(Category.WATER, "WATER", "BUBBLE_COLUMN", "KELP", "KELP_PLANT", "SEAGRASS", "TALL_SEAGRASS");
        register(Category.LAVA, "LAVA", "LAVA_CAULDRON");
        register(Category.FIRE, "FIRE", "SOUL_FIRE", "CAMPFIRE", "SOUL_CAMPFIRE");
        register(Category.OTHER, "CACTUS", "MAGMA_BLOCK", "POWDER_SNOW", "SWEET_BERRY_BUSH", "WITHER_ROSE",
                "COBWEB", "POINTED_DRIPSTONE", "NETHER_PORTAL", "END_PORTAL", "END_GATEWAY", "TNT");
    }

    public void register(Category category, String... materialNames) {
        for (String n : materialNames) byName.put(n.toUpperCase(Locale.ROOT), category);
    }

    /** @return the hazard category, or null when the block type is not a known hazard. */
    public Category classify(String materialName) {
        return materialName == null ? null : byName.get(materialName.toUpperCase(Locale.ROOT));
    }
}
