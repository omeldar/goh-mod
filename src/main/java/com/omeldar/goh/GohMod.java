package com.omeldar.goh;

import com.omeldar.goh.net.GohPayload;
import com.omeldar.goh.server.AdminCommand;
import com.omeldar.goh.server.GohServer;
import com.omeldar.goh.server.ServerConfig;
import com.omeldar.goh.server.SpawnGuard;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.omeldar.goh.server.TerrainInfo;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point, run on the dedicated server and inside single player alike.
 *
 * <p>Registers the two packet types (both sides must know them), the join hello, the shared
 * waypoint list and {@code /gohadmin}. Everything on the client side lives in
 * {@link com.omeldar.goh.client.GohClient}.
 */
public final class GohMod implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("goh");

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(GohPayload.ToClient.ID, GohPayload.ToClient.CODEC);
        PayloadTypeRegistry.playC2S().register(GohPayload.ToServer.ID, GohPayload.ToServer.CODEC);

        ServerConfig.load();
        AdminCommand.register();

        ServerPlayNetworking.registerGlobalReceiver(GohPayload.ToServer.ID,
                (payload, ctx) -> GohServer.onMessage(ctx.player(), payload));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SpawnGuard.onJoin(handler.getPlayer(), server);
            GohServer.onJoin(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SpawnGuard.onLeave(handler.getPlayer(), server));
        ServerTickEvents.END_SERVER_TICK.register(SpawnGuard::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpawnGuard::allowDamage);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GohServer.WAYPOINTS.unload());
        ServerLifecycleEvents.SERVER_STARTED.register(TerrainInfo::refresh);
        ServerTickEvents.END_SERVER_TICK.register(TerrainInfo::tick);

        LOG.info("goh {} ready", version());
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer("goh")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }
}
