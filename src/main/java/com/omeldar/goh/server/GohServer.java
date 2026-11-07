package com.omeldar.goh.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.omeldar.goh.GohMod;
import com.omeldar.goh.SharedWaypoint;
import com.omeldar.goh.net.GohPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;

/** The server half: says hello, and keeps the shared waypoint list. */
public final class GohServer {
    private static final Gson GSON = new Gson();
    public static final WaypointStore WAYPOINTS = new WaypointStore();

    private GohServer() {}

    public static boolean isOwner(ServerPlayerEntity p) {
        return p.getPermissions().hasPermission(new Permission.Level(PermissionLevel.OWNERS));
    }

    static WaypointStore.Who who(ServerPlayerEntity p) {
        return new WaypointStore.Who(p.getName().getString(), p.getUuidAsString(), isOwner(p));
    }

    // ------------------------------------------------------------------- join

    public static void onJoin(ServerPlayerEntity p) {
        ServerConfig cfg = ServerConfig.get();
        if (ServerPlayNetworking.canSend(p, GohPayload.ToClient.ID)) {
            JsonObject hello = new JsonObject();
            hello.addProperty("serverName", cfg.serverName);
            JsonArray lines = new JsonArray();
            cfg.welcome.forEach(lines::add);
            hello.add("welcome", lines);
            hello.addProperty("website", cfg.website);
            hello.addProperty("waypoints", cfg.waypointSharing);
            hello.addProperty("distantTerrain", TerrainInfo.lssPresent());
            hello.addProperty("terrainColumns", TerrainInfo.columns());
            hello.addProperty("terrainMibPerSecond", TerrainInfo.perPlayerMib());
            hello.addProperty("terrainDistance", TerrainInfo.distanceChunks());
            hello.addProperty("owner", isOwner(p));
            hello.addProperty("version", GohMod.version());
            // Which of the family the server runs, so the client (and Lumi) know that e.g.
            // /keeper exists here.
            JsonObject mods = new JsonObject();
            for (String id : new String[]{"goh", "keeper", "lss", "worldgrow"}) {
                FabricLoader.getInstance().getModContainer(id).ifPresent(c ->
                        mods.addProperty(id, c.getMetadata().getVersion().getFriendlyString()));
            }
            hello.add("serverMods", mods);
            send(p, "hello", hello);
            return;
        }

        // No mod on this client: the welcome goes to chat, with the way to get the full one.
        p.sendMessage(Text.literal("Welcome to " + cfg.serverName + "!").formatted(Formatting.AQUA));
        for (String line : cfg.welcome) {
            p.sendMessage(Text.literal(line).formatted(Formatting.GRAY));
        }
        if (cfg.modpackUrl != null && !cfg.modpackUrl.isBlank()) {
            Text link = Text.literal(cfg.modpackUrl).setStyle(Style.EMPTY
                    .withColor(Formatting.BLUE).withUnderline(true)
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(cfg.modpackUrl))));
            p.sendMessage(Text.literal("The GoH modpack adds shared waypoints, distant terrain "
                    + "and more: ").formatted(Formatting.GRAY).append(link));
        }
    }

    // ---------------------------------------------------------------- messages

    public static void onMessage(ServerPlayerEntity p, GohPayload.ToServer msg) {
        MinecraftServer server = p.getEntityWorld().getServer();
        try {
            JsonObject body = msg.json().isBlank() ? new JsonObject()
                    : JsonParser.parseString(msg.json()).getAsJsonObject();
            switch (msg.kind()) {
                case "waypoints" -> sendWaypoints(p, server);
                case "share" -> {
                    SharedWaypoint w = GSON.fromJson(body, SharedWaypoint.class);
                    String refused = WAYPOINTS.put(server, who(p), w);
                    result(p, refused == null, refused == null
                            ? "shared \"" + w.name.trim() + "\" with the server" : refused);
                    if (refused == null) broadcast(server);
                }
                case "remove" -> {
                    String name = body.has("name") ? body.get("name").getAsString() : "";
                    String refused = WAYPOINTS.remove(server, who(p), name);
                    result(p, refused == null, refused == null
                            ? "removed \"" + name + "\" from the server" : refused);
                    if (refused == null) broadcast(server);
                }
                case "screen" -> SpawnGuard.onScreen(p, server, body.has("open") && body.get("open").getAsBoolean());
                // A newer client may say things this server does not know; stay quiet about it
                // rather than put an error in the player's chat.
                default -> GohMod.LOG.debug("ignoring unknown message {} from {}", msg.kind(), p.getName().getString());
            }
        } catch (Exception e) {
            GohMod.LOG.warn("bad {} message from {}", msg.kind(), p.getName().getString(), e);
            result(p, false, "the server could not handle that: " + e.getMessage());
        }
    }

    private static void sendWaypoints(ServerPlayerEntity p, MinecraftServer server) {
        JsonObject o = new JsonObject();
        o.add("list", GSON.toJsonTree(WAYPOINTS.all(server)));
        o.addProperty("you", p.getUuidAsString());
        o.addProperty("owner", isOwner(p));
        send(p, "waypoints", o);
    }

    /** Everyone online with the mod gets the new list, so imports are never stale for long. */
    private static void broadcast(MinecraftServer server) {
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            sendWaypoints(other, server);
        }
    }

    private static void result(ServerPlayerEntity p, boolean ok, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", ok);
        o.addProperty("message", message);
        send(p, "result", o);
    }

    private static void send(ServerPlayerEntity p, String kind, JsonObject body) {
        if (!ServerPlayNetworking.canSend(p, GohPayload.ToClient.ID)) return;
        ServerPlayNetworking.send(p, new GohPayload.ToClient(kind, GSON.toJson(body)));
    }
}
