package com.omeldar.goh.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.omeldar.goh.SharedWaypoint;
import com.omeldar.goh.client.map.Maps;
import com.omeldar.goh.client.ui.SetupScreen;
import com.omeldar.goh.client.ui.WelcomeScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * {@code /goh}, for players who prefer typing to the welcome screen. Every branch calls the
 * same {@link Actions} the screen does.
 */
final class Commands {
    private Commands() {}

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("goh")
                        .executes(ctx -> open(WelcomeScreen.Page.MAIN))
                        .then(literal("lumi").executes(ctx -> open(WelcomeScreen.Page.LUMI)))
                        .then(literal("setup").executes(ctx -> {
                            GohClient.openLater(new SetupScreen());
                            return 1;
                        }))
                        .then(literal("terrain")
                                .executes(ctx -> say("distant terrain: " + Actions.terrainState()))
                                .then(literal("on").executes(ctx -> say(Actions.terrain(true))))
                                .then(literal("off").executes(ctx -> say(Actions.terrain(false)))))
                        .then(literal("waypoints")
                                .executes(ctx -> list())
                                .then(literal("import").executes(ctx -> say(Actions.importWaypoints())))
                                .then(literal("share")
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .suggests((ctx, b) -> {
                                                    for (SharedWaypoint w : Maps.ownWaypoints()) b.suggest(w.name);
                                                    return b.buildFuture();
                                                })
                                                .executes(ctx -> say(Actions.shareFromMap(name(ctx))))))
                                .then(literal("here")
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> say(Actions.shareHere(name(ctx))))))
                                .then(literal("remove")
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .suggests((ctx, b) -> {
                                                    for (SharedWaypoint w : Session.waypoints) {
                                                        if (Session.mayChange(w)) b.suggest(w.name);
                                                    }
                                                    return b.buildFuture();
                                                })
                                                .executes(ctx -> say(Actions.removeShared(name(ctx)))))))));
    }

    private static String name(CommandContext<FabricClientCommandSource> ctx) {
        return StringArgumentType.getString(ctx, "name");
    }

    private static int open(WelcomeScreen.Page page) {
        GohClient.openLater(new WelcomeScreen(page));
        return 1;
    }

    private static int list() {
        if (!Session.gohServer) return say("this server does not run the GoH mod");
        if (Session.waypoints.isEmpty()) return say("nothing has been shared on this server yet");
        say(Session.waypoints.size() + " shared waypoints:");
        for (SharedWaypoint w : Session.waypoints) {
            say("  " + w.name + "  " + w.where() + "  by " + w.owner);
        }
        return 1;
    }

    private static int say(String message) {
        GohClient.say(message, true);
        return 1;
    }
}
