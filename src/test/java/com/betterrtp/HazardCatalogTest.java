package com.betterrtp;

import com.betterrtp.rtp.HazardCatalog;
import com.betterrtp.rtp.HazardCatalog.Category;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HazardCatalogTest {
    private final HazardCatalog catalog = new HazardCatalog();

    @Test void requiredHazardsAreClassified() {
        assertEquals(Category.WATER, catalog.classify("WATER"));
        assertEquals(Category.LAVA, catalog.classify("LAVA"));
        assertEquals(Category.FIRE, catalog.classify("FIRE"));
        assertEquals(Category.FIRE, catalog.classify("SOUL_FIRE"));
        assertEquals(Category.FIRE, catalog.classify("CAMPFIRE"));
        assertEquals(Category.FIRE, catalog.classify("SOUL_CAMPFIRE"));
        for (String n : new String[]{"CACTUS", "MAGMA_BLOCK", "POWDER_SNOW", "SWEET_BERRY_BUSH", "WITHER_ROSE"}) {
            assertEquals(Category.OTHER, catalog.classify(n), n);
        }
    }

    @Test void safeBlocksAreNotHazards() {
        assertNull(catalog.classify("GRASS_BLOCK"));
        assertNull(catalog.classify("AIR"));
        assertNull(catalog.classify("FIREFLY_BUSH"));
        assertNull(catalog.classify(null));
    }

    @Test void lookupIsCaseInsensitiveAndExtensible() {
        assertEquals(Category.LAVA, catalog.classify("lava"));
        catalog.register(Category.OTHER, "honey_block");
        assertEquals(Category.OTHER, catalog.classify("HONEY_BLOCK"));
    }
}
