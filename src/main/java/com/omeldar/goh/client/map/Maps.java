package com.omeldar.goh.client.map;

import com.omeldar.goh.GohMod;
import com.omeldar.goh.SharedWaypoint;

import java.util.ArrayList;
import java.util.List;

/**
 * The map mod, from the rest of the mod's point of view. Today that is JourneyMap, the map
 * the GoH pack ships; the methods are phrased so another map could stand behind them.
 *
 * <p>Nothing here names a JourneyMap class, so this is safe to call with JourneyMap absent.
 * {@link #available} only turns true when JourneyMap has loaded the plugin.
 */
public final class Maps {
    static volatile boolean available;

    private Maps() {}

    public static boolean available() {
        return available;
    }

    public static List<SharedWaypoint> ownWaypoints() {
        if (!available) return new ArrayList<>();
        try {
            return JourneyMapPlugin.own();
        } catch (Throwable t) {
            GohMod.LOG.warn("could not read waypoints from JourneyMap", t);
            return new ArrayList<>();
        }
    }

    /** The player's own waypoint with this name, ignoring case, or null. */
    public static SharedWaypoint ownWaypoint(String name) {
        for (SharedWaypoint w : ownWaypoints()) if (w.name.equalsIgnoreCase(name)) return w;
        return null;
    }

    /** How many were added, or -1 when there is no map to add them to. */
    public static int importShared(List<SharedWaypoint> list) {
        if (!available) return -1;
        try {
            return JourneyMapPlugin.replaceImported(list);
        } catch (Throwable t) {
            GohMod.LOG.warn("could not add waypoints to JourneyMap", t);
            return -1;
        }
    }

    public static int importedCount() {
        if (!available) return 0;
        try {
            return JourneyMapPlugin.importedCount();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String groupName() {
        return JourneyMapPlugin.GROUP;
    }
}
