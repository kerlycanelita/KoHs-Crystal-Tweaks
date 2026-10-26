package com.zymekoh.crystaltweaks.practice;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The practice world's own commands, there and nowhere else: /rtp, /spawn, /kit and /bot. They run
 * on the practice world's integrated server, never on a server the player is connected to.
 */
final class PracticeCommands {
    private PracticeCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rtp").requires(PracticeCommands::inPractice).executes(context -> {
            CommandSourceStack source = context.getSource();
            ServerPlayer player = source.getPlayerOrException();
            int[] spot = session(source).randomTeleport(player);
            if (spot == null) {
                source.sendFailure(Component.literal(CrystalUi.spanish()
                        ? "No se encontró suelo seco: prueba otra vez."
                        : "No dry ground found: try again."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal((CrystalUi.spanish() ? "Teletransportado a " : "Teleported to ")
                    + spot[0] + ", " + spot[1]), false);
            return 1;
        }));
        dispatcher.register(Commands.literal("spawn").requires(PracticeCommands::inPractice).executes(context -> {
            session(context.getSource()).backToSpawn(context.getSource().getPlayerOrException());
            return 1;
        }));
        dispatcher.register(Commands.literal("kit").requires(PracticeCommands::inPractice).executes(context -> {
            session(context.getSource()).reequip(context.getSource().getPlayerOrException());
            return 1;
        }));
        LiteralArgumentBuilder<CommandSourceStack> bot = Commands.literal("bot").requires(PracticeCommands::inPractice);
        bot.then(Commands.literal("here").executes(context -> {
            session(context.getSource()).botHere(context.getSource().getPlayerOrException());
            return 1;
        }));
        bot.then(setting("on", () -> CrystalVisualConfig.setPracticeBot(true)));
        bot.then(setting("off", () -> CrystalVisualConfig.setPracticeBot(false)));
        for (PracticeSettings.BotStyle style : PracticeSettings.BotStyle.values()) {
            bot.then(setting(style.name().toLowerCase(java.util.Locale.ROOT), () -> {
                CrystalVisualConfig.setPracticeBot(true);
                CrystalVisualConfig.setPracticeBotStyle(style.name());
            }));
        }
        for (PracticeSettings.Difficulty difficulty : PracticeSettings.Difficulty.values()) {
            bot.then(setting(difficulty.name().toLowerCase(java.util.Locale.ROOT),
                    () -> CrystalVisualConfig.setPracticeBotDifficulty(difficulty.name())));
        }
        dispatcher.register(bot);
    }

    /** A /bot word that changes the saved setting, so the panel shows it too, and brings the bot back with it. */
    private static LiteralArgumentBuilder<CommandSourceStack> setting(String word, Runnable change) {
        return Commands.literal(word).executes(context -> {
            // The settings file belongs to the game thread, like everything the panel writes.
            Minecraft.getInstance().execute(() -> {
                change.run();
                CrystalVisualConfig.save();
                PracticeSession.settingsChanged();
            });
            context.getSource().sendSuccess(() -> Component.literal("Bot · " + word), false);
            return 1;
        });
    }

    private static boolean inPractice(CommandSourceStack source) {
        return PracticeSession.of(source.getServer()) != null;
    }

    /** Only reached through {@link #inPractice}, so the session is there. */
    private static PracticeSession session(CommandSourceStack source) {
        return java.util.Objects.requireNonNull(PracticeSession.of(source.getServer()));
    }
}
