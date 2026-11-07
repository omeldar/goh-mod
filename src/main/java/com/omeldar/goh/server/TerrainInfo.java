package com.omeldar.goh.server;

import com.omeldar.goh.GohMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a player needs to know before switching distant terrain on: roughly how much there is,
 * and how fast this server sends it.
 *
 * <p>"How much" is the number of generated overworld chunks - LSS only sends terrain that
 * exists, and one chunk is one LSS column. It is counted from the region file headers (4 KiB
 * each, 1,024 slots), on a background thread, at start-up and every ten minutes, so it costs
 * the game nothing. "How fast" is LSS's per-player bandwidth cap, read from its config.
 */
public final class TerrainInfo {
    private static final Pattern PER_PLAYER = Pattern.compile("(?m)^\\s*per_player_mib_per_second:\\s*([0-9.]+)");
    private static final Pattern OVERWORLD = Pattern.compile("(?m)^\\s*\"minecraft:overworld\":\\s*(\\d+)");
    private static final AtomicBoolean counting = new AtomicBoolean();

    private static volatile long generatedChunks = -1;
    private static volatile double perPlayerMib;
    private static volatile int distanceChunks;
    private static int sinceRefresh;

    private TerrainInfo() {}

    public static boolean lssPresent() {
        return FabricLoader.getInstance().isModLoaded("lss");
    }

    /** Generated chunks within LSS's reach, or -1 while not yet counted. */
    public static long columns() {
        long n = generatedChunks;
        if (n < 0) return -1;
        if (distanceChunks > 0) {
            long side = 2L * distanceChunks + 1;
            n = Math.min(n, side * side);
        }
        return n;
    }

    public static double perPlayerMib() {
        return perPlayerMib;
    }

    public static int distanceChunks() {
        return distanceChunks;
    }

    /** Called every server tick; refreshes every ten minutes, off the main thread. */
    public static void tick(MinecraftServer server) {
        if (!lssPresent()) return;
        if (++sinceRefresh >= 20 * 60 * 10) refresh(server);
    }

    public static void refresh(MinecraftServer server) {
        if (!lssPresent()) return;
        sinceRefresh = 0;
        if (!counting.compareAndSet(false, true)) return;
        Path region = server.getSavePath(WorldSavePath.ROOT).resolve("region");
        Thread t = new Thread(() -> {
            try {
                readConfig();
                generatedChunks = count(region);
            } catch (Exception e) {
                GohMod.LOG.warn("could not count generated chunks", e);
            } finally {
                counting.set(false);
            }
        }, "goh-terrain-count");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static void readConfig() {
        Path f = FabricLoader.getInstance().getConfigDir().resolve("lss-server-config.yaml");
        try {
            if (!Files.exists(f)) return;
            String text = Files.readString(f, StandardCharsets.UTF_8);
            Matcher m = PER_PLAYER.matcher(text);
            if (m.find()) perPlayerMib = Double.parseDouble(m.group(1));
            m = OVERWORLD.matcher(text);
            if (m.find()) distanceChunks = Integer.parseInt(m.group(1));
        } catch (Exception e) {
            GohMod.LOG.warn("could not read {}", f, e);
        }
    }

    private static long count(Path region) throws Exception {
        if (!Files.isDirectory(region)) return 0;
        long total = 0;
        byte[] header = new byte[4096];
        try (DirectoryStream<Path> files = Files.newDirectoryStream(region, "r.*.mca")) {
            for (Path f : files) {
                try (InputStream in = Files.newInputStream(f)) {
                    int read = in.readNBytes(header, 0, header.length);
                    ByteBuffer b = ByteBuffer.wrap(header, 0, read);
                    while (b.remaining() >= 4) {
                        if (b.getInt() != 0) total++;
                    }
                }
            }
        }
        return total;
    }
}
