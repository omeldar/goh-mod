package com.omeldar.goh.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.omeldar.goh.GohMod;
import com.omeldar.goh.SharedWaypoint;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The server's shared waypoint list, kept in the world folder as {@code goh-waypoints.json}.
 *
 * <p>The rules are short. Nothing arrives here unless a player sends it on purpose - there is
 * no syncing of anyone's map. A name is unique on the server, ignoring case. Sharing under a
 * name that already exists overwrites it, and so does removing, but only for the player who
 * shared it first or the server owner. Everyone else is refused and told whose it is.
 *
 * <p>Only touched on the server thread, so there is no locking.
 */
public final class WaypointStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "goh-waypoints.json";

    private final List<SharedWaypoint> list = new ArrayList<>();
    private Path loadedFrom;

    /** Who is asking, as far as the rules care. */
    public record Who(String name, String uuid, boolean owner) {}

    public synchronized List<SharedWaypoint> all(MinecraftServer server) {
        ensure(server);
        List<SharedWaypoint> out = new ArrayList<>(list.size());
        for (SharedWaypoint w : list) out.add(w.copy());
        out.sort(Comparator.comparing(w -> w.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Null on success, otherwise the reason it was refused. */
    public synchronized String put(MinecraftServer server, Who who, SharedWaypoint in) {
        ensure(server);
        ServerConfig cfg = ServerConfig.get();
        if (!cfg.waypointSharing) return "waypoint sharing is turned off on this server";

        String name = clean(in.name);
        if (name.isEmpty()) return "a waypoint needs a name";
        if (name.length() > 48) return "names are at most 48 characters";
        if (Math.abs(in.x) > 30_000_000 || Math.abs(in.z) > 30_000_000 || in.y < -2048 || in.y > 2048) {
            return "those coordinates are outside the world";
        }
        if (server.getWorld(net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.WORLD,
                net.minecraft.util.Identifier.tryParse(in.dimension))) == null) {
            return "this server has no dimension called " + in.dimension;
        }

        SharedWaypoint existing = find(name);
        if (existing != null && !mayChange(who, existing)) {
            return "\"" + existing.name + "\" belongs to " + existing.owner
                 + ". Only they or the server owner can overwrite it - pick another name.";
        }
        if (existing == null) {
            if (list.size() >= cfg.maxWaypointsTotal) {
                return "the server list is full (" + cfg.maxWaypointsTotal + " waypoints)";
            }
            long mine = list.stream().filter(w -> w.ownerUuid.equals(who.uuid())).count();
            if (!who.owner() && mine >= cfg.maxWaypointsPerPlayer) {
                return "you have shared " + mine + ", the most one player can. Remove one first.";
            }
        }

        SharedWaypoint w = existing != null ? existing : new SharedWaypoint();
        w.name = name;
        w.x = in.x;
        w.y = in.y;
        w.z = in.z;
        w.dimension = in.dimension;
        if (existing == null) {
            w.owner = who.name();
            w.ownerUuid = who.uuid();
            list.add(w);
        }
        w.updatedBy = who.name();
        w.updatedAt = Instant.now().toString();
        save();
        GohMod.LOG.info("{} {} waypoint {} at {}", who.name(),
                existing == null ? "shared" : "overwrote", name, w.where());
        return null;
    }

    /** Null on success, otherwise the reason it was refused. */
    public synchronized String remove(MinecraftServer server, Who who, String rawName) {
        ensure(server);
        SharedWaypoint existing = find(clean(rawName));
        if (existing == null) return "there is no shared waypoint called \"" + rawName + "\"";
        if (!mayChange(who, existing)) {
            return "\"" + existing.name + "\" belongs to " + existing.owner
                 + ". Only they or the server owner can remove it.";
        }
        list.remove(existing);
        save();
        GohMod.LOG.info("{} removed waypoint {}", who.name(), existing.name);
        return null;
    }

    private static boolean mayChange(Who who, SharedWaypoint w) {
        return who.owner() || w.ownerUuid.equals(who.uuid());
    }

    private SharedWaypoint find(String name) {
        for (SharedWaypoint w : list) {
            if (w.name.equalsIgnoreCase(name)) return w;
        }
        return null;
    }

    private static String clean(String s) {
        if (s == null) return "";
        // Drop formatting codes and control characters; a name is shown in other people's maps.
        return s.replaceAll("§.", "").replaceAll("\\p{Cntrl}", "").trim();
    }

    private void ensure(MinecraftServer server) {
        Path f = server.getSavePath(WorldSavePath.ROOT).resolve(FILE).normalize();
        if (f.equals(loadedFrom)) return;
        list.clear();
        loadedFrom = f;
        try {
            if (Files.exists(f)) {
                List<SharedWaypoint> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        new TypeToken<List<SharedWaypoint>>() {}.getType());
                if (read != null) list.addAll(read);
            }
        } catch (Exception e) {
            GohMod.LOG.warn("could not read {}; starting with an empty list and leaving the "
                    + "file alone", f, e);
            // Refuse to overwrite a file we could not read, or one bad edit costs everyone's list.
            loadedFrom = null;
            throw new IllegalStateException("the waypoint file could not be read: " + e.getMessage());
        }
    }

    private void save() {
        if (loadedFrom == null) return;
        try {
            Path tmp = loadedFrom.resolveSibling(FILE + ".tmp");
            Files.writeString(tmp, GSON.toJson(list), StandardCharsets.UTF_8);
            Files.move(tmp, loadedFrom, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            GohMod.LOG.warn("could not save the waypoint list", e);
        }
    }

    /** Forget the loaded list, so the next world opened in this session reads its own. */
    public synchronized void unload() {
        list.clear();
        loadedFrom = null;
    }
}
