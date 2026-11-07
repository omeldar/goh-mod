package com.omeldar.goh.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.omeldar.goh.SharedWaypoint;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /gohadmin}, for the console and the server owner. Not {@code /goh}: that one is the
 * client's, and two commands with one name would leave whichever loses unreachable.
 */
public final class AdminCommand {
    private AdminCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
                literal("gohadmin")
                        .requires(s -> s.getPermissions().hasPermission(
                                new Permission.Level(PermissionLevel.OWNERS)))
                        .then(literal("waypoints")
                                .executes(ctx -> list(ctx.getSource()))
                                .then(literal("remove")
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> remove(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"))))))
                        .then(literal("reload").executes(ctx -> {
                            ServerConfig.load();
                            ctx.getSource().sendFeedback(() -> Text.literal("goh config reloaded")
                                    .formatted(Formatting.GREEN), false);
                            return 1;
                        }))));
    }

    private static int list(ServerCommandSource source) {
        List<SharedWaypoint> all = GohServer.WAYPOINTS.all(source.getServer());
        source.sendFeedback(() -> Text.literal(all.size() + " shared waypoints")
                .formatted(Formatting.AQUA), false);
        for (SharedWaypoint w : all) {
            source.sendFeedback(() -> Text.literal("  " + w.name + "  " + w.where() + "  by "
                    + w.owner).formatted(Formatting.GRAY), false);
        }
        return all.size();
    }

    private static int remove(ServerCommandSource source, String name) {
        // The console and the owner are both "the server owner" for the store's rules.
        String refused = GohServer.WAYPOINTS.remove(source.getServer(),
                new WaypointStore.Who(source.getName(), "", true), name);
        source.sendFeedback(() -> Text.literal(refused == null ? "removed " + name : refused)
                .formatted(refused == null ? Formatting.GREEN : Formatting.YELLOW), false);
        return refused == null ? 1 : 0;
    }
}
