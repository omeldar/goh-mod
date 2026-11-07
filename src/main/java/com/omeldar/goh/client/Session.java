package com.omeldar.goh.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.omeldar.goh.SharedWaypoint;
import com.omeldar.goh.net.GohPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * What this client knows about the server it is on: whether that server runs the GoH mod,
 * what it said in its hello, and the shared waypoint list as last received.
 *
 * <p>Touched from the client thread only; the bridge reads it through ClientQueue.
 */
public final class Session {
    private static final Gson GSON = new Gson();

    public static boolean gohServer;
    public static String serverName = "";
    public static List<String> welcome = new ArrayList<>();
    public static String website = "";
    public static boolean waypointsOffered;
    public static boolean terrainOffered;
    /** Generated chunks the server could send, -1 if it has not counted yet. */
    public static long terrainColumns = -1;
    public static double terrainMibPerSecond;
    public static boolean owner;
    public static String myUuid = "";

    public static List<SharedWaypoint> waypoints = new ArrayList<>();
    public static boolean waypointsLoaded;
    public static String lastResult = "";
    public static boolean lastOk = true;
    /** Bumped on every server answer, so a waiting caller can tell a new one arrived. */
    public static int answers;
    /** Bumped only on a result, which answers a share or a remove. */
    public static int results;

    private Session() {}

    public static void reset() {
        gohServer = false;
        serverName = "";
        welcome = new ArrayList<>();
        website = "";
        waypointsOffered = false;
        terrainOffered = false;
        terrainColumns = -1;
        terrainMibPerSecond = 0;
        owner = false;
        myUuid = "";
        waypoints = new ArrayList<>();
        waypointsLoaded = false;
        lastResult = "";
        lastOk = true;
    }

    /** A stable name for the server, for "have we welcomed you here before". */
    public static String serverKey(MinecraftClient mc) {
        if (mc.isInSingleplayer()) return "singleplayer";
        var entry = mc.getCurrentServerEntry();
        return entry == null ? "unknown" : entry.address.toLowerCase(java.util.Locale.ROOT);
    }

    public static void onMessage(GohPayload.ToClient msg) {
        JsonObject o = JsonParser.parseString(msg.json()).getAsJsonObject();
        switch (msg.kind()) {
            case "hello" -> {
                gohServer = true;
                serverName = str(o, "serverName", "the server");
                welcome = new ArrayList<>();
                if (o.has("welcome")) o.getAsJsonArray("welcome").forEach(e -> welcome.add(e.getAsString()));
                website = str(o, "website", "");
                waypointsOffered = o.has("waypoints") && o.get("waypoints").getAsBoolean();
                terrainOffered = o.has("distantTerrain") && o.get("distantTerrain").getAsBoolean();
                owner = o.has("owner") && o.get("owner").getAsBoolean();
                terrainColumns = o.has("terrainColumns") ? o.get("terrainColumns").getAsLong() : -1;
                terrainMibPerSecond = o.has("terrainMibPerSecond") ? o.get("terrainMibPerSecond").getAsDouble() : 0;
                GohClient.onHello();
            }
            case "waypoints" -> {
                List<SharedWaypoint> list = GSON.fromJson(o.get("list"),
                        new TypeToken<List<SharedWaypoint>>() {}.getType());
                waypoints = list == null ? new ArrayList<>() : list;
                myUuid = str(o, "you", myUuid);
                owner = o.has("owner") ? o.get("owner").getAsBoolean() : owner;
                waypointsLoaded = true;
                answers++;
            }
            case "result" -> {
                lastOk = o.has("ok") && o.get("ok").getAsBoolean();
                lastResult = str(o, "message", "");
                results++;
                answers++;
                GohClient.say(lastResult, lastOk);
            }
            default -> {
                // A newer server may say things this client does not know yet; ignore them.
            }
        }
    }

    public static boolean canTalk() {
        return gohServer && ClientPlayNetworking.canSend(GohPayload.ToServer.ID);
    }

    public static void send(String kind, JsonObject body) {
        if (!canTalk()) return;
        ClientPlayNetworking.send(new GohPayload.ToServer(kind, GSON.toJson(body)));
    }

    public static void requestWaypoints() {
        send("waypoints", new JsonObject());
    }

    /** The upload. Only ever called because the player, or Lumi at their request, asked. */
    public static void share(SharedWaypoint w) {
        send("share", GSON.toJsonTree(w).getAsJsonObject());
    }

    public static void remove(String name) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        send("remove", o);
    }

    public static boolean mayChange(SharedWaypoint w) {
        return owner || w.ownerUuid.equals(myUuid);
    }

    public static SharedWaypoint find(String name) {
        for (SharedWaypoint w : waypoints) if (w.name.equalsIgnoreCase(name)) return w;
        return null;
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }
}
