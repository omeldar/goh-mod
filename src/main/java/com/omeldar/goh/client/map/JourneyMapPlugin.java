package com.omeldar.goh.client.map;

import com.omeldar.goh.GohMod;
import com.omeldar.goh.SharedWaypoint;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.common.waypoint.WaypointGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The only class that touches JourneyMap. JourneyMap finds it through the {@code journeymap}
 * entry point and calls {@link #initialize}; without JourneyMap it is never loaded, so the
 * rest of the mod has no JourneyMap classes to trip over. JourneyMap 6 still refuses a plugin
 * without the annotation, though its API marks it for removal. Everything else goes through
 * {@link Maps}.
 */
@SuppressWarnings("removal")
@journeymap.api.v2.client.JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapPlugin implements IClientPlugin {
    static final String GROUP = "GoH server";
    private static IClientAPI api;

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
        Maps.available = true;
        GohMod.LOG.info("journeymap found; shared waypoints can be imported into it");
    }

    @Override
    public String getModId() {
        return "goh";
    }

    /** The player's own waypoints, the ones they might want to share. */
    static List<SharedWaypoint> own() {
        List<SharedWaypoint> out = new ArrayList<>();
        if (api == null) return out;
        for (Waypoint w : api.getAllWaypoints()) {
            if ("goh".equals(w.getModId())) continue; // our imports are not theirs to re-share
            BlockPos pos = w.getBlockPos();
            if (pos == null) continue;
            SharedWaypoint s = new SharedWaypoint();
            s.name = w.getName() == null ? "" : w.getName();
            s.x = pos.getX();
            s.y = pos.getY();
            s.z = pos.getZ();
            String dim = w.getPrimaryDimension();
            s.dimension = dim == null || dim.isBlank() ? "minecraft:overworld" : dim;
            out.add(s);
        }
        return out;
    }

    /**
     * Replace the "GoH server" group with the given list. Replacing rather than merging means
     * a waypoint the server dropped disappears here too, and an overwritten one moves.
     */
    @SuppressWarnings("deprecation") // every factory overload is deprecated in API 2.0.0, with no successor yet
    static int replaceImported(List<SharedWaypoint> list) {
        if (api == null) return -1;
        api.removeAllWaypoints("goh");
        WaypointGroup group = api.getWaypointGroupByName("goh", GROUP);
        if (group == null) {
            group = WaypointFactory.createWaypointGroup("goh", GROUP);
            group.setDescription("Waypoints shared on the GoH server. Re-import to refresh.");
            api.addWaypointGroup(group);
        }
        int added = 0;
        for (SharedWaypoint s : list) {
            if (Identifier.tryParse(s.dimension) == null) continue;
            Waypoint w = WaypointFactory.createClientWaypoint("goh",
                    new BlockPos(s.x, s.y, s.z), s.name, s.dimension, true);
            w.setCustomData("owner", s.owner);
            group.addWaypoint(w);
            api.addWaypoint("goh", w);
            added++;
        }
        return added;
    }

    static int importedCount() {
        if (api == null) return 0;
        return api.getWaypoints("goh").size();
    }
}
