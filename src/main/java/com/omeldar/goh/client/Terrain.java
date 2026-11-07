package com.omeldar.goh.client;

import com.omeldar.goh.GohMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Distant terrain, which is LOD Server Support (LSS) feeding the server's map into Voxy.
 *
 * <p>This class does not transfer anything itself. It owns one switch: LSS's
 * {@code lod.receive}. Until the player chooses, it is held off, because a first sync can be
 * a lot of data and that should be somebody's decision. Choosing writes the setting into
 * LSS's own config file and asks LSS to reload it.
 *
 * <p>LSS is reached by reflection, so nothing here breaks when it is missing or renames a
 * method: the switch then just reports that it is not available.
 */
public final class Terrain {
    private static final Pattern RECEIVE = Pattern.compile("(?m)^(\\s*receive:\\s*)(true|false)");

    private Terrain() {}

    public static boolean installed() {
        return FabricLoader.getInstance().isModLoaded("lss");
    }

    public static boolean voxyInstalled() {
        return FabricLoader.getInstance().isModLoaded("voxy");
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("lss-client-config.yaml");
    }

    /** Whether LSS is downloading right now, or null when that cannot be read. */
    public static Boolean receiving() {
        try {
            Object cfg = Class.forName("dev.vox.lss.config.LSSClientConfig").getField("CONFIG").get(null);
            return (Boolean) cfg.getClass().getMethod("receiveServerLods").invoke(cfg);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Hold LSS to the player's choice. Called at start-up and whenever they change it. */
    public static void apply() {
        if (!installed()) return;
        boolean want = "on".equals(ClientConfig.get().distantTerrain);
        Boolean now = receiving();
        if (now != null && now == want) return;
        String failed = write(want);
        if (failed != null) {
            GohMod.LOG.warn("distant terrain: {}", failed);
            return;
        }
        reload(msg -> GohMod.LOG.info("lss: {}", msg.getString()));
    }

    /** Null on success, otherwise what went wrong, in words for the player. */
    public static String set(boolean on) {
        if (!installed()) {
            return "LOD Server Support is not installed, so there is no distant terrain to load";
        }
        ClientConfig.get().distantTerrain = on ? "on" : "off";
        ClientConfig.save();
        String failed = write(on);
        if (failed != null) return failed;
        // LSS applies a reload on its next tick and then opens or closes its session with the
        // server by itself, so there is nothing to wait for here.
        reload(msg -> GohMod.LOG.info("lss: {}", msg.getString()));
        return null;
    }

    private static String write(boolean on) {
        Path f = file();
        try {
            if (!Files.exists(f)) return "LSS has not written its config yet; join a world first";
            String text = Files.readString(f, StandardCharsets.UTF_8);
            Matcher m = RECEIVE.matcher(text);
            if (!m.find()) return "could not find lod.receive in " + f.getFileName();
            String next = text.substring(0, m.start(2)) + on + text.substring(m.end(2));
            if (!next.equals(text)) Files.writeString(f, next, StandardCharsets.UTF_8);
            return null;
        } catch (Exception e) {
            return "could not update " + f.getFileName() + ": " + e.getMessage();
        }
    }

    private static void reload(Consumer<Text> feedback) {
        try {
            Object cfg = Class.forName("dev.vox.lss.config.LSSClientConfig").getField("CONFIG").get(null);
            cfg.getClass().getMethod("reload", Consumer.class).invoke(cfg, feedback);
        } catch (Throwable t) {
            GohMod.LOG.warn("could not ask LSS to reload; it will pick the change up next start", t);
        }
    }
}
