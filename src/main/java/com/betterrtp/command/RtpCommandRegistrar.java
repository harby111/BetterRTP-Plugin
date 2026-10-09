package com.betterrtp.command;

import com.betterrtp.BetterRTPPlugin;
import com.betterrtp.config.Messages;
import com.betterrtp.config.Msg;
import com.betterrtp.rtp.RtpService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Registers /rtp (+ /brtp alias) and /rtpgui through Paper's Brigadier lifecycle API. */
public final class RtpCommandRegistrar {
    private RtpCommandRegistrar() { }

    public static void register(BetterRTPPlugin plugin, RtpService service) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands c = event.registrar();

            var rtp = Commands.literal("rtp")
                    .executes(ctx -> player(ctx, service, "betterrtp.use", p -> service.openMenu(p, false)))
                    .then(Commands.literal("help").executes(ctx -> {
                        service.messages().send(ctx.getSource().getSender(), Msg.HELP);
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(Commands.literal("reload").executes(ctx -> {
                        CommandSender s = ctx.getSource().getSender();
                        if (!s.hasPermission("betterrtp.reload")) { service.messages().send(s, Msg.NO_PERMISSION); return 0; }
                        boolean ok = plugin.reloadAll();
                        service.messages().send(s, ok ? Msg.RELOAD_SUCCESS : Msg.RELOAD_FAILURE);
                        return ok ? Command.SINGLE_SUCCESS : 0;
                    }))
                    .then(Commands.literal("biome")
                            .executes(ctx -> { service.messages().send(ctx.getSource().getSender(), Msg.USAGE); return 0; })
                            .then(Commands.argument("biome", StringArgumentType.greedyString())
                                    .suggests((ctx, b) -> suggestBiomes(b))
                                    .executes(ctx -> {
                                        String raw = StringArgumentType.getString(ctx, "biome").trim();
                                        return player(ctx, service, "betterrtp.biome", p -> {
                                            NamespacedKey key = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
                                            Biome biome = key == null ? null : Registry.BIOME.get(key);
                                            if (biome == null) {
                                                service.messages().send(p, Msg.INVALID_BIOME, Messages.p("biome", raw));
                                                return;
                                            }
                                            service.startBiome(p, key);
                                        });
                                    })))
                    .then(Commands.literal("world")
                            .executes(ctx -> { service.messages().send(ctx.getSource().getSender(), Msg.USAGE); return 0; })
                            .then(Commands.argument("world", StringArgumentType.word())
                                    .suggests((ctx, b) -> suggestWorlds(b))
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "world");
                                        return player(ctx, service, "betterrtp.world", p -> service.startWorld(p, name));
                                    })))
                    .build();
            c.register(rtp, "Random teleport", List.of("brtp"));

            var gui = Commands.literal("rtpgui")
                    .executes(ctx -> {
                        if (!service.config().rtpguiCommandEnabled()) {
                            service.messages().send(ctx.getSource().getSender(), Msg.RTPGUI_DISABLED);
                            return 0;
                        }
                        return player(ctx, service, "betterrtp.use", p -> service.openMenu(p, true));
                    })
                    .build();
            c.register(gui, "Open the RTP menu");
        });
    }

    /** Permission + player-only handling shared by every player command. */
    private static int player(CommandContext<CommandSourceStack> ctx, RtpService service, String perm, Consumer<Player> action) {
        CommandSender sender = ctx.getSource().getSender();
        Entity executor = ctx.getSource().getExecutor();
        if (!sender.hasPermission(perm)) { service.messages().send(sender, Msg.NO_PERMISSION); return 0; }
        Player p = executor instanceof Player ep ? ep : sender instanceof Player sp ? sp : null;
        if (p == null) { service.messages().send(sender, Msg.PLAYER_ONLY); return 0; }
        action.accept(p);
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestBiomes(SuggestionsBuilder b) {
        String in = b.getRemainingLowerCase();
        for (Biome biome : Registry.BIOME) {
            NamespacedKey k = Registry.BIOME.getKey(biome);
            if (k == null) continue;
            String shown = NamespacedKey.MINECRAFT.equals(k.getNamespace()) ? k.getKey() : k.toString();
            if (shown.startsWith(in) || k.toString().startsWith(in)) b.suggest(shown);
        }
        return b.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestWorlds(SuggestionsBuilder b) {
        String in = b.getRemainingLowerCase();
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().toLowerCase(Locale.ROOT).startsWith(in)) b.suggest(w.getName());
        }
        return b.buildFuture();
    }
}
