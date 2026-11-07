package com.omeldar.goh.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.omeldar.goh.GohMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Nobody dies reading the setup the server puts in front of them - once.
 *
 * <p>A player's first join gets a short grace, held for as long as the setup that the server
 * opened for them is on screen (up to five minutes), and ended two seconds after it closes or
 * as soon as they walk off. After that the player is written down in the world folder
 * ({@code goh-protected.json}) and never protected again: opening {@code /goh} or
 * {@code /goh setup} later is their own choice, made wherever they like, and a screen that
 * made them invulnerable on demand would be a free escape from any fight.
 *
 * <p>The client says whether a screen was forced, but the server only listens for players not
 * yet on the list, so a modified client gains nothing by claiming it. {@code /kill} and the
 * void always apply, so nobody can be stuck.
 */
public final class SpawnGuard {
    private static final Gson GSON = new Gson();
    private static final String FILE = "goh-protected.json";
    private static final int JOIN_GRACE = 20 * 15;
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
    private static final Set<String> protectedOnce = new HashSet<>();
    private static Path loadedFrom;

    private SpawnGuard() {}

    public static void onJoin(ServerPlayerEntity p, MinecraftServer server) {
        ensure(server);
        if (protectedOnce.contains(p.getUuidAsString())) return;
        guards.put(p.getUuid(), new Guard(p.getEntityPos(), server.getTicks()));
    }

    public static void onLeave(ServerPlayerEntity p, MinecraftServer server) {
        if (guards.remove(p.getUuid()) != null) used(server, p.getUuid());
    }

    /** The client says a GoH screen opened or closed, and whether the server's setup forced it. */
    public static void onScreen(ServerPlayerEntity p, MinecraftServer server, boolean open, boolean forced) {
        Guard g = guards.get(p.getUuid());
        if (g == null) return; // already used up: protection is a first-start thing only
        int now = server.getTicks();
        if (open && forced) {
            g.screenOpen = true;
            g.untilTick = g.joinedTick + MAX;
        } else if (!open && g.screenOpen) {
            g.screenOpen = false;
            g.untilTick = Math.min(g.joinedTick + MAX, now + AFTER_CLOSE);
        }
        // A screen the player opened by hand changes nothing; the short join grace runs out.
    }

    public static void tick(MinecraftServer server) {
        if (guards.isEmpty() || server.getTicks() % 5 != 0) return;
        int now = server.getTicks();
        var it = guards.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Guard g = e.getValue();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            boolean end = p == null || now > g.untilTick;
            if (!end && !g.screenOpen) {
                Vec3d at = p.getEntityPos();
                double dx = at.x - g.pos.x;
                double dz = at.z - g.pos.z;
                // Walking off ends it; falling straight down (a join in mid-air) does not.
                end = dx * dx + dz * dz > WALKED_OFF * WALKED_OFF;
            }
            if (end) {
                it.remove();
                used(server, e.getKey());
            }
        }
    }

    /** False cancels the damage. Wired to Fabric's ALLOW_DAMAGE. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity p) || !guards.containsKey(p.getUuid())) return true;
        return source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD);
    }

    private static void used(MinecraftServer server, UUID id) {
        ensure(server);
        if (protectedOnce.add(id.toString())) save();
    }

    private static void ensure(MinecraftServer server) {
        Path f = server.getSavePath(WorldSavePath.ROOT).resolve(FILE).normalize();
        if (f.equals(loadedFrom)) return;
        protectedOnce.clear();
        loadedFrom = f;
        try {
            if (Files.exists(f)) {
                List<String> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        new TypeToken<List<String>>() {}.getType());
                if (read != null) protectedOnce.addAll(read);
            }
        } catch (Exception e) {
            GohMod.LOG.warn("could not read {}; first-join protection may repeat for some players", f, e);
        }
    }

    private static void save() {
        if (loadedFrom == null) return;
        try {
            Files.writeString(loadedFrom, GSON.toJson(protectedOnce.stream().sorted().toList()),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            GohMod.LOG.warn("could not save {}", loadedFrom, e);
        }
    }
}
