package com.betterrtp.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

/** MiniMessage-backed message catalogue. Nothing user-facing is hardcoded in Java. */
public final class Messages {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('§').build();

    private final Map<Msg, String> raw = new EnumMap<>(Msg.class);
    private final String prefix;

    private Messages(String prefix) { this.prefix = prefix; }

    public static Messages from(ConfigurationSection root) {
        String prefix = root.getString("messages.prefix", "");
        Messages m = new Messages(prefix);
        for (Msg key : Msg.values()) {
            String value;
            if (root.isList(key.path())) value = String.join("\n", root.getStringList(key.path()));
            else value = root.getString(key.path(), "<red>[missing message: " + key.path() + "]");
            m.raw.put(key, value);
        }
        return m;
    }

    public static TagResolver p(String key, Object value) { return Placeholder.unparsed(key, String.valueOf(value)); }

    public boolean has(Msg msg) {
        String s = raw.get(msg);
        return s != null && !s.isEmpty();
    }

    public Component render(Msg msg, TagResolver... resolvers) {
        return parse(raw.getOrDefault(msg, ""), resolvers);
    }

    /** Parses any configured MiniMessage string (GUI titles, item names, ...). */
    public Component parse(String text, TagResolver... resolvers) {
        if (text == null || text.isEmpty()) return Component.empty();
        return MM.deserialize(text, TagResolver.builder().resolvers(resolvers)
                .resolver(Placeholder.parsed("prefix", prefix)).build());
    }

    public void send(Audience audience, Msg msg, TagResolver... resolvers) {
        if (has(msg)) audience.sendMessage(render(msg, resolvers));
    }

    /** Bedrock forms only understand legacy colour codes (hex is downsampled to the nearest colour). */
    public String legacy(String text, TagResolver... resolvers) {
        return LEGACY.serialize(parse(text, resolvers));
    }
}
