package com.betterrtp.util;

import com.betterrtp.config.NameChecks;

public final class BukkitNameChecks implements NameChecks {
    @Override public boolean material(String name) { return Parsers.material(name).isPresent(); }
    @Override public boolean sound(String name) { return Parsers.sound(name).isPresent(); }
    @Override public boolean particle(String name) { return Parsers.particle(name).isPresent(); }
}
