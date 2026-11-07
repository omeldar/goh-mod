package com.omeldar.goh.server;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nobody dies reading the welcome screen.
 *
 * <p>A player who just joined cannot be hurt for a short grace period, and for as long as a GoH
 * screen is open (welcome, Lumi introduction, distant terrain question, sharing), up to five
 * minutes. It ends a couple of seconds after the screen closes, or as soon as the player walks
 * off, whichever comes first - with a screen open nobody can fight or build, so there is nothing
 * to exploit. {@code /kill} and the void still apply, so nobody can be stuck.
 *
 * <p>Players without the mod get the same grace on join, just without the screen.
 */
public final class SpawnGuard {
    private static final int JOIN_GRACE = 20 * 10;
    private static final int AFTER_CLOSE = 20 * 2;
    private static final int MAX = 20 * 60 * 5;
    private static final double WALKED_OFF = 1.5;

    private static final class Guard {
        final Vec3d pos;
        final int joinedTick;
        int untilTick;
        boolean screenOpen;

        Guard(Vec3d pos, int now) {
            this.pos = pos;
            this.joinedTick = now;
            this.untilTick = now + JOIN_GRACE;
        }
    }

    private static final Map<UUID, Guard> guards = new HashMap<>();

    private SpawnGuard() {}

    public static void onJoin(ServerPlayerEntity p, MinecraftServer server) {
        guards.put(p.getUuid(), new Guard(p.getEntityPos(), server.getTicks()));
    }

    public static void onLeave(ServerPlayerEntity p) {
        guards.remove(p.getUuid());
    }

    /** The client says a GoH screen opened or closed. */
    public static void onScreen(ServerPlayerEntity p, MinecraftServer server, boolean open) {
        Guard g = guards.get(p.getUuid());
        if (g == null) return; // already walked off or timed out; a screen later is not a shield
        int now = server.getTicks();
        g.screenOpen = open;
        g.untilTick = open ? g.joinedTick + MAX : Math.min(g.joinedTick + MAX, now + AFTER_CLOSE);
    }

    public static void tick(MinecraftServer server) {
        if (guards.isEmpty() || server.getTicks() % 5 != 0) return;
        int now = server.getTicks();
        guards.entrySet().removeIf(e -> {
            Guard g = e.getValue();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null || now > g.untilTick) return true;
            Vec3d at = p.getEntityPos();
            double dx = at.x - g.pos.x;
            double dz = at.z - g.pos.z;
            // Walking off ends it; falling straight down (a join in mid-air) does not.
            return !g.screenOpen && dx * dx + dz * dz > WALKED_OFF * WALKED_OFF;
        });
    }

    /** False cancels the damage. Wired to Fabric's ALLOW_DAMAGE. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity p) || !guards.containsKey(p.getUuid())) return true;
        return source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD);
    }
}
