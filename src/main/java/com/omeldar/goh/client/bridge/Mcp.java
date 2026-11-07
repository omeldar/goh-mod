package com.omeldar.goh.client.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.omeldar.goh.SharedWaypoint;
import com.omeldar.goh.client.Actions;
import com.omeldar.goh.client.GohClient;
import com.omeldar.goh.client.Session;
import com.omeldar.goh.client.Terrain;
import com.omeldar.goh.client.map.Maps;

/**
 * The tools Lumi gets, under the {@code goh_} prefix.
 *
 * <p>Each is something the player can already do from the welcome screen or {@code /goh},
 * and goes through the same {@link Actions}, so the same rules hold: nothing is uploaded
 * unless asked for, and only an owner or the server owner can overwrite or remove a shared
 * waypoint.
 */
final class Mcp {
    private Mcp() {}

    static JsonArray toolList() {
        JsonArray tools = new JsonArray();
        tools.add(tool("get_status",
                "Whether this server runs the GoH mod, what it offers (shared waypoints, distant "
              + "terrain), whether JourneyMap, LOD Server Support, Voxy and Lumi are installed, "
              + "and whether distant terrain is switched on. Call this first.",
                "{\"type\":\"object\",\"properties\":{},\"required\":[]}"));
        tools.add(tool("list_shared_waypoints",
                "The server's shared waypoint list: name, coordinates, dimension, who shared it, "
              + "and whether this player may change it.",
                "{\"type\":\"object\",\"properties\":{},\"required\":[]}"));
        tools.add(tool("list_my_waypoints",
                "The player's own JourneyMap waypoints, which are the ones they could share.",
                "{\"type\":\"object\",\"properties\":{},\"required\":[]}"));
        tools.add(tool("import_waypoints",
                "Copy the server's shared waypoints into the player's JourneyMap, in their own "
              + "\"GoH server\" group. Replaces the previous import; the player's own waypoints "
              + "are not touched.",
                "{\"type\":\"object\",\"properties\":{},\"required\":[]}"));
        tools.add(tool("share_waypoint",
                "Upload one waypoint to the server's shared list. Only when the player has asked "
              + "to share that specific waypoint - never on your own initiative. source \"map\" "
              + "shares the player's JourneyMap waypoint with that name; \"here\" shares the "
              + "block they stand on under that name. Sharing a name that exists overwrites it, "
              + "which only its owner or the server owner may do; otherwise the server refuses "
              + "and says whose it is.",
                "{\"type\":\"object\",\"properties\":{"
              + "\"name\":{\"type\":\"string\"},"
              + "\"source\":{\"type\":\"string\",\"enum\":[\"map\",\"here\"]}},"
              + "\"required\":[\"name\",\"source\"]}"));
        tools.add(tool("remove_shared_waypoint",
                "Remove a waypoint from the server's shared list. Only when the player asks. "
              + "Allowed for its owner and the server owner.",
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},"
              + "\"required\":[\"name\"]}"));
        tools.add(tool("set_distant_terrain",
                "Switch distant terrain (LOD Server Support feeding the server's map to Voxy) on "
              + "or off. Turning it on can download a lot of data, so do it when the player asks.",
                "{\"type\":\"object\",\"properties\":{\"on\":{\"type\":\"boolean\"}},"
              + "\"required\":[\"on\"]}"));
        return tools;
    }

    static String call(String name, JsonObject args) throws Exception {
        return switch (name) {
            case "get_status" -> ClientQueue.on(mc -> status(mc != null && mc.world != null));
            case "list_shared_waypoints" -> ClientQueue.on(mc -> sharedList());
            case "list_my_waypoints" -> ClientQueue.on(mc -> ownList());
            case "import_waypoints" -> ClientQueue.on(mc -> Actions.importWaypoints());
            case "share_waypoint" -> {
                String wp = str(args, "name");
                boolean here = "here".equals(str(args, "source"));
                yield awaitServer(() -> here ? Actions.shareHere(wp) : Actions.shareFromMap(wp));
            }
            case "remove_shared_waypoint" -> {
                String wp = str(args, "name");
                yield awaitServer(() -> Actions.removeShared(wp));
            }
            case "set_distant_terrain" -> {
                boolean on = args.has("on") && args.get("on").getAsBoolean();
                yield ClientQueue.on(mc -> Actions.terrain(on));
            }
            default -> "unknown tool " + name;
        };
    }

    /**
     * Run an action that sends something to the server, then wait briefly for the server's
     * verdict, so the model hears "refused: belongs to X" rather than "sent".
     */
    private static String awaitServer(java.util.function.Supplier<String> action) throws Exception {
        int before = ClientQueue.on(mc -> Session.results);
        String sent = ClientQueue.on(mc -> action.get());
        if (!(sent.startsWith("sharing") || sent.startsWith("overwriting") || sent.startsWith("removing"))) {
            return sent;
        }
        for (int i = 0; i < 50; i++) {
            Thread.sleep(100);
            if (ClientQueue.on(mc -> Session.results) != before) {
                return ClientQueue.on(mc -> (Session.lastOk ? "" : "refused: ") + Session.lastResult);
            }
        }
        return sent + ", but no answer arrived within 5 s";
    }

    private static String status(boolean inWorld) {
        JsonObject o = new JsonObject();
        o.addProperty("inWorld", inWorld);
        o.addProperty("gohServer", Session.gohServer);
        o.addProperty("serverName", Session.serverName);
        o.addProperty("serverOffersSharedWaypoints", Session.waypointsOffered);
        o.addProperty("serverOffersDistantTerrain", Session.terrainOffered);
        o.addProperty("playerIsServerOwner", Session.owner);
        o.addProperty("sharedWaypointCount", Session.waypoints.size());
        o.addProperty("journeymapInstalled", Maps.available());
        o.addProperty("importedWaypoints", Maps.importedCount());
        o.addProperty("lodServerSupportInstalled", Terrain.installed());
        o.addProperty("voxyInstalled", Terrain.voxyInstalled());
        o.addProperty("distantTerrain", Actions.terrainState());
        o.addProperty("lumiInstalled", GohClient.lumiInstalled());
        return o.toString();
    }

    private static String sharedList() {
        if (!Session.gohServer) return "this server does not run the GoH mod";
        JsonArray a = new JsonArray();
        for (SharedWaypoint w : Session.waypoints) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name);
            o.addProperty("x", w.x);
            o.addProperty("y", w.y);
            o.addProperty("z", w.z);
            o.addProperty("dimension", w.dimension);
            o.addProperty("owner", w.owner);
            o.addProperty("youMayChange", Session.mayChange(w));
            a.add(o);
        }
        return a.toString();
    }

    private static String ownList() {
        if (!Maps.available()) return "JourneyMap is not installed";
        JsonArray a = new JsonArray();
        for (SharedWaypoint w : Maps.ownWaypoints()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name);
            o.addProperty("where", w.where());
            a.add(o);
        }
        return a.toString();
    }

    private static JsonObject tool(String name, String description, String schema) {
        JsonObject t = new JsonObject();
        t.addProperty("name", name);
        t.addProperty("description", description);
        t.add("inputSchema", JsonParser.parseString(schema));
        return t;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}
