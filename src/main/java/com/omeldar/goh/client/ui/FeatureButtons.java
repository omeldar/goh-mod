package com.omeldar.goh.client.ui;

import com.omeldar.goh.client.Actions;
import com.omeldar.goh.client.GohClient;
import com.omeldar.goh.client.Session;
import com.omeldar.goh.client.Terrain;
import com.omeldar.goh.client.map.Maps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * The two things the server offers a player, as buttons. Both the welcome screen and the setup
 * walkthrough show them, and they must not drift apart, so they are built in one place.
 */
final class FeatureButtons {
    private FeatureButtons() {}

    /** Distant terrain through Voxy and LOD Server Support. Off until the player says yes. */
    static ButtonWidget terrain(MinecraftClient client, Screen owner, int x, int y, int w, Runnable refresh) {
        String label;
        boolean usable = true;
        if (!Session.terrainOffered) {
            label = "Distant terrain: not offered here";
            usable = false;
        } else if (!Terrain.installed()) {
            label = "Distant terrain: needs LOD Server Support";
            usable = false;
        } else {
            label = Boolean.TRUE.equals(Terrain.receiving())
                    ? "Distant terrain: on (stop)" : "Load distant terrain...";
        }
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> {
            if (client == null) return;
            if (Boolean.TRUE.equals(Terrain.receiving())) {
                GohClient.say(Actions.terrain(false), true);
                refresh.run();
            } else {
                client.setScreen(new TerrainConfirmScreen(owner));
            }
        }).dimensions(x, y, w, 20).build();
        b.active = usable;
        b.setTooltip(Tooltip.of(Text.literal("Sends the server's map to Voxy, so you can see far "
                + "past your render distance. Off until you choose; you can stop it any time.")));
        return b;
    }

    /** Pull the server's shared waypoint list into JourneyMap. */
    static ButtonWidget importWaypoints(int x, int y, int w, Runnable refresh) {
        String label;
        boolean usable = true;
        if (!Session.waypointsOffered) {
            label = "Shared waypoints: not offered here";
            usable = false;
        } else if (!Maps.available()) {
            label = "Shared waypoints: needs JourneyMap";
            usable = false;
        } else {
            label = "Import shared waypoints"
                    + (Session.waypointsLoaded ? " (" + Session.waypoints.size() + ")" : "");
        }
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> {
            GohClient.say(Actions.importWaypoints(), true);
            refresh.run();
        }).dimensions(x, y, w, 20).build();
        b.active = usable;
        b.setTooltip(Tooltip.of(Text.literal("Adds the places players have shared to a \""
                + Maps.groupName() + "\" group in JourneyMap. Your own waypoints are not touched.")));
        return b;
    }
}
