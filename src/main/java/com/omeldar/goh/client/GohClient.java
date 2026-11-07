package com.omeldar.goh.client;

import com.omeldar.goh.GohMod;
import com.omeldar.goh.client.bridge.ApiServer;
import com.omeldar.goh.client.bridge.LumiLink;
import com.omeldar.goh.client.ui.SetupScreen;
import com.omeldar.goh.client.ui.WelcomeScreen;
import com.omeldar.goh.net.GohPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The client half.
 *
 * <p>Costs nothing in play: one tick listener that counts to forty after a join and then
 * does nothing, no rendering, no polling. The welcome screen opens by itself at most once per
 * server, and the Lumi introduction once ever; after that it is {@code /goh}.
 */
public final class GohClient implements ClientModInitializer {
    /** Ticks after joining before anything opens, so the world has finished appearing. */
    private static final int SETTLE_TICKS = 40;

    private static int sinceJoin = -1;
    private static boolean decided;
    private static Screen pending;
    /** Whether a GoH screen was open last tick; the server protects the player while it is. */
    private static boolean screenWasOpen;
    /** True from the moment the server's first-join setup is opened until it is closed. */
    private static boolean forced;

    @Override
    public void onInitializeClient() {
        ClientConfig.load();

        ClientPlayNetworking.registerGlobalReceiver(GohPayload.ToClient.ID,
                (payload, ctx) -> Session.onMessage(payload));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            Session.reset();
            sinceJoin = 0;
            decided = false;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            TerrainProgress.end(false);
            Session.reset();
            sinceJoin = -1;
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            // Hold LSS to the player's choice before any server connection exists, so no
            // terrain is pulled before they have said yes.
            Terrain.apply();
            ApiServer.start();
            LumiLink.announce();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            LumiLink.withdraw();
            ApiServer.stop();
        });

        ClientTickEvents.END_CLIENT_TICK.register(GohClient::tick);
        Commands.register();
        Keybinds.register();
        GohMod.LOG.info("goh client ready");
    }

    private static void tick(MinecraftClient mc) {
        TerrainProgress.tick(mc);
        Keybinds.onTick(mc);
        boolean gohScreen = mc.currentScreen instanceof com.omeldar.goh.client.ui.GohScreen;
        if (gohScreen != screenWasOpen) {
            screenWasOpen = gohScreen;
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("open", gohScreen);
            o.addProperty("forced", gohScreen && forced);
            Session.send("screen", o);
            if (!gohScreen) forced = false;
        }
        if (pending != null && mc.currentScreen == null) {
            Screen s = pending;
            pending = null;
            mc.setScreen(s);
            return;
        }
        if (sinceJoin < 0 || decided || mc.player == null) return;
        if (++sinceJoin < SETTLE_TICKS || mc.currentScreen != null) return;
        decided = true;

        ClientConfig cfg = ClientConfig.get();
        String key = Session.serverKey(mc);
        if (Session.gohServer && !cfg.welcomed.contains(key)) {
            cfg.welcomed.add(key);
            ClientConfig.save();
            forced = true;
            mc.setScreen(cfg.setupDone ? new WelcomeScreen(WelcomeScreen.Page.MAIN) : new SetupScreen());
        } else if (!cfg.lumiIntroSeen && lumiInstalled()) {
            mc.setScreen(new WelcomeScreen(WelcomeScreen.Page.LUMI));
        }
    }

    /** Whether the screen on show is the one the server opened on first join (the protected one). */
    public static boolean forcedSetup() {
        return forced;
    }

    /** The hello arrived. Fetch the shared list so it is ready when someone asks. */
    static void onHello() {
        if (Session.waypointsOffered) Session.requestWaypoints();
        // A returning player who already said yes gets the same progress lines as a new one.
        if (Session.terrainOffered && Boolean.TRUE.equals(Terrain.receiving())) TerrainProgress.begin();
    }

    /** Open a screen on the next tick, after a chat screen that ran a command has closed. */
    public static void openLater(Screen s) {
        pending = s;
    }

    public static boolean lumiInstalled() {
        return FabricLoader.getInstance().isModLoaded("lumipet");
    }

    public static void say(String message, boolean ok) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || message == null || message.isBlank()) return;
        mc.player.sendMessage(Text.literal("[GoH] ").formatted(Formatting.DARK_AQUA)
                .append(Text.literal(message).formatted(ok ? Formatting.GRAY : Formatting.YELLOW)), false);
    }
}
