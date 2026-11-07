package com.omeldar.goh.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Keeps the player told while distant terrain loads: a chat line every five minutes with how
 * much has arrived, how much is left and roughly how long, and a [Stop] they can click. A last
 * line when it has caught up. Reads LSS's own status counters, by reflection, once a second.
 */
public final class TerrainProgress {
    /**
     * Raw LSS bytes per column. Measured on a vanilla 1.21.11 test world: 26,107 columns came to
     * 972 MB, about 37 KiB each, and they arrived at almost exactly the 4 MiB/s per-player cap.
     */
    public static final double BYTES_PER_COLUMN = 37 * 1024;
    /** A long download should not fill chat; Lumi can answer "how far is it" in between. */
    private static final int REPORT_EVERY_SECONDS = 300;
    private static final int RATE_WINDOW_SECONDS = 30;
    private static final int QUIET_SECONDS_FOR_DONE = 20;

    private static boolean active;
    private static long startColumns;
    private static long startMillis;
    private static long lastReportMillis;
    private static long windowColumns;
    private static long windowMillis;
    private static double rate; // columns per second
    private static long quietSince;
    private static boolean toldNothingYet;
    private static int tickCounter;

    private TerrainProgress() {}

    public static boolean active() {
        return active;
    }

    /** Distant terrain was just switched on: start watching. */
    public static void begin() {
        Snapshot s = Snapshot.read();
        active = true;
        startColumns = s == null ? 0 : s.received;
        startMillis = System.currentTimeMillis();
        lastReportMillis = startMillis;
        windowColumns = startColumns;
        windowMillis = startMillis;
        rate = 0;
        quietSince = 0;
        toldNothingYet = false;
    }

    /** Switched off, or the connection ended. Says how far it got if anything arrived. */
    public static void end(boolean byPlayer) {
        if (!active) return;
        active = false;
        if (!byPlayer) return;
        Snapshot s = Snapshot.read();
        long got = s == null ? 0 : Math.max(0, s.received - startColumns);
        if (got > 0) {
            GohClient.say("distant terrain stopped after " + fmt(got) + " columns. What loaded stays "
                    + "visible; /goh terrain on picks up where it left off.", true);
        }
    }

    public static void tick(MinecraftClient mc) {
        if (!active || ++tickCounter % 20 != 0) return;
        if (mc.player == null) return;
        Snapshot s = Snapshot.read();
        if (s == null || !s.reception) return;

        long now = System.currentTimeMillis();
        long got = Math.max(0, s.received - startColumns);

        // Rate over a sliding ~30 s window, so a slow start does not stick to the estimate.
        if (now - windowMillis >= RATE_WINDOW_SECONDS * 1000L) {
            double fresh = (s.received - windowColumns) * 1000.0 / Math.max(1, now - windowMillis);
            rate = rate == 0 ? fresh : rate * 0.4 + fresh * 0.6;
            windowColumns = s.received;
            windowMillis = now;
        }

        boolean idle = s.queued == 0;
        if (idle && got > 0) {
            if (quietSince == 0) quietSince = now;
            if (now - quietSince >= QUIET_SECONDS_FOR_DONE * 1000L) {
                active = false;
                GohClient.say("distant terrain is up to date: " + fmt(got) + " columns in "
                        + duration((now - startMillis) / 1000) + ". It keeps itself current while "
                        + "you play.", true);
                return;
            }
        } else {
            quietSince = 0;
        }

        if (got == 0 && !toldNothingYet && now - startMillis > 60_000) {
            toldNothingYet = true;
            GohClient.say("nothing has arrived yet. " + (s.negotiated ? "The server may be busy; it "
                    + "will start when it can." : "The server is not sending distant terrain to this "
                    + "client - check /lss diag."), false);
        }

        if (now - lastReportMillis >= REPORT_EVERY_SECONDS * 1000L && got > 0) {
            lastReportMillis = now;
            report(got);
        }
    }

    private static long lastGot;

    /** One line for Lumi or /goh terrain: how far the download is right now. */
    public static String statusLine() {
        if (!active) {
            Boolean on = Terrain.receiving();
            return Boolean.TRUE.equals(on) ? "on, nothing left to download" : "not downloading";
        }
        Snapshot s = Snapshot.read();
        long got = s == null ? lastGot : Math.max(0, s.received - startColumns);
        StringBuilder b = new StringBuilder("downloading: ").append(fmt(got)).append(" columns so far");
        long total = Session.terrainColumns;
        if (total > 0) {
            b.append(" of ~").append(fmt(total)).append(" (").append(Math.min(99, got * 100 / Math.max(1, total))).append("%)");
            if (rate > 0.5) b.append(", roughly ").append(duration((long) (Math.max(0, total - got) / rate))).append(" left");
        }
        return b.append("; /goh terrain off stops it").toString();
    }

    private static void report(long got) {
        lastGot = got;
        StringBuilder line = new StringBuilder("distant terrain: ").append(fmt(got)).append(" columns");
        long total = Session.terrainColumns;
        if (total > 0) {
            long pct = Math.min(99, got * 100 / Math.max(1, total));
            line.append(" (about ").append(pct).append("% of ~").append(fmt(total)).append(")");
            if (rate > 0.5) {
                long left = (long) (Math.max(0, total - got) / rate);
                line.append(", roughly ").append(duration(left)).append(" left");
            }
        } else if (rate > 0.5) {
            line.append(String.format(Locale.ROOT, ", %.0f a second", rate));
        }
        line.append(". ");
        say(Text.literal(line.toString()).formatted(Formatting.GRAY).append(stopLink()));
    }

    /** A clickable [Stop] that runs /goh terrain off. */
    public static MutableText stopLink() {
        return Text.literal("[Stop]").setStyle(Style.EMPTY.withColor(Formatting.RED).withUnderline(true)
                .withClickEvent(new ClickEvent.RunCommand("/goh terrain off"))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Stop loading distant terrain"))));
    }

    private static void say(Text body) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal("[GoH] ").formatted(Formatting.DARK_AQUA).append(body), false);
    }

    /** How long loading the server's terrain is likely to take, as words, or null if unknown. */
    public static String estimate() {
        long total = Session.terrainColumns;
        double mib = Session.terrainMibPerSecond;
        if (total <= 0 || mib <= 0) return null;
        double fastest = total * BYTES_PER_COLUMN / (mib * 1024 * 1024);
        // Measured runs land just above the bandwidth cap; LSS starts gently and yields to
        // normal game traffic, so allow up to twice that on a busy server.
        long low = (long) Math.max(60, fastest);
        long high = (long) Math.max(120, fastest * 2);
        return "about " + fmt(total) + " columns of terrain, roughly " + duration(low) + " to "
             + duration(high) + " at this server's speed";
    }

    static String duration(long seconds) {
        if (seconds < 90) return Math.max(1, seconds) + " s";
        long minutes = Math.round(seconds / 60.0);
        if (minutes < 90) return minutes + " min";
        return String.format(Locale.ROOT, "%.1f h", minutes / 60.0);
    }

    static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    /** The few LSS status numbers this needs. */
    record Snapshot(long received, long bytes, int queued, boolean reception, boolean negotiated) {
        private static Method latest;

        static Snapshot read() {
            try {
                if (latest == null) {
                    latest = Class.forName("dev.vox.lss.networking.client.ClientStatus").getMethod("latest");
                }
                Object s = latest.invoke(null);
                if (s == null) return null;
                Class<?> c = s.getClass();
                return new Snapshot(
                        ((Number) c.getMethod("receivedColumns").invoke(s)).longValue(),
                        ((Number) c.getMethod("receivedBytes").invoke(s)).longValue(),
                        ((Number) c.getMethod("queuedColumns").invoke(s)).intValue(),
                        (Boolean) c.getMethod("receptionEnabled").invoke(s),
                        (Boolean) c.getMethod("negotiated").invoke(s));
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
