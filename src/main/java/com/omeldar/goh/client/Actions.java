package com.omeldar.goh.client;

import com.omeldar.goh.SharedWaypoint;
import com.omeldar.goh.client.map.Maps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Everything a player can do with the mod, in one place: the welcome screen, the {@code /goh}
 * commands and Lumi's tools all call these, so they behave the same and refuse the same way.
 * Client thread only.
 *
 * <p>Every method answers in a sentence. A refusal says what is missing and what to do, so a
 * player reading chat and a model reading a tool result can both act on it.
 */
public final class Actions {
    private Actions() {}

    // ------------------------------------------------------------- waypoints

    public static String importWaypoints() {
        String why = whyNoWaypoints(true);
        if (why != null) return why;
        if (!Session.waypointsLoaded) {
            Session.requestWaypoints();
            return "asked the server for its list; try again in a moment";
        }
        int added = Maps.importShared(Session.waypoints);
        if (added < 0) return "JourneyMap would not take the waypoints; see the log";
        return "imported " + added + " shared waypoint" + (added == 1 ? "" : "s")
             + " into the \"" + Maps.groupName() + "\" group in JourneyMap";
    }

    /** Share one of the player's own JourneyMap waypoints, by name. */
    public static String shareFromMap(String name) {
        String why = whyNoWaypoints(true);
        if (why != null) return why;
        SharedWaypoint w = Maps.ownWaypoint(name);
        if (w == null) {
            return "there is no waypoint called \"" + name + "\" in your JourneyMap. Check the "
                 + "name, or share where you are standing instead.";
        }
        return shareChecked(w);
    }

    /** Share the block the player is standing on, under a name they chose. */
    public static String shareHere(String name) {
        String why = whyNoWaypoints(false);
        if (why != null) return why;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return "not in a world";
        SharedWaypoint w = new SharedWaypoint();
        w.name = name.trim();
        w.x = p.getBlockX();
        w.y = p.getBlockY();
        w.z = p.getBlockZ();
        w.dimension = p.getEntityWorld().getRegistryKey().getValue().toString();
        return shareChecked(w);
    }

    private static String shareChecked(SharedWaypoint w) {
        if (w.name.isBlank()) return "a shared waypoint needs a name";
        // The server decides who may overwrite what; the list here can be a moment stale (say,
        // just after someone was made owner), so this only picks the right word.
        SharedWaypoint existing = Session.find(w.name);
        Session.share(w);
        return (existing == null ? "sharing" : "overwriting") + " \"" + w.name + "\" at "
             + w.where() + " - the server will confirm";
    }

    public static String removeShared(String name) {
        String why = whyNoWaypoints(false);
        if (why != null) return why;
        SharedWaypoint existing = Session.find(name);
        String target = existing == null ? name.trim() : existing.name;
        Session.remove(target);
        return "removing \"" + target + "\" - the server will confirm";
    }

    private static String whyNoWaypoints(boolean needMap) {
        if (!Session.gohServer) {
            return "this server does not run the GoH mod, so there is no shared waypoint list here";
        }
        if (!Session.waypointsOffered) return "waypoint sharing is turned off on this server";
        if (!Session.canTalk()) return "the server is not accepting GoH messages right now";
        if (needMap && !Maps.available()) {
            return "JourneyMap is not installed, and it is where shared waypoints go";
        }
        return null;
    }

    // ---------------------------------------------------------------- terrain

    public static String terrain(boolean on) {
        if (!Terrain.installed()) {
            return "LOD Server Support is not installed. It is what brings the server's map "
                 + "to Voxy; the GoH modpack includes it.";
        }
        boolean was = Boolean.TRUE.equals(Terrain.receiving());
        String failed = Terrain.set(on);
        if (failed != null) return failed;
        if (!on) {
            TerrainProgress.end(was);
            return "distant terrain is off. What already loaded stays visible.";
        }
        if (Session.terrainOffered) TerrainProgress.begin();
        String note = Session.terrainOffered ? ""
                : " This server does not send any, but others with LSS will.";
        String estimate = TerrainProgress.estimate();
        return "distant terrain is on" + (estimate == null ? "" : ": " + estimate) + ". Progress "
             + "shows in chat every five minutes, and /goh terrain off stops it at any point."
             + (Terrain.voxyInstalled() ? "" : " Install Voxy to see it.") + note;
    }

    public static String terrainState() {
        if (!Terrain.installed()) return "not installed";
        if (TerrainProgress.active()) return TerrainProgress.statusLine();
        Boolean now = Terrain.receiving();
        String choice = ClientConfig.get().distantTerrain;
        return (now == null ? "unknown" : now ? "on" : "off")
             + ("ask".equals(choice) ? " (not chosen yet)" : "");
    }
}
