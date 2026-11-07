package com.omeldar.goh.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.omeldar.goh.GohMod;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** {@code config/goh/client.json}: what this player has already seen and chosen. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientConfig instance;

    /** The Lumi introduction is shown once, on the first world joined after install. */
    public boolean lumiIntroSeen = false;
    /** Servers whose welcome screen has been shown; it opens by itself only the first time. */
    public List<String> welcomed = new ArrayList<>();
    /**
     * Distant terrain from the server, through LOD Server Support: "ask" until the player
     * decides, then "on" or "off". Anything but "on" keeps downloads off, so nobody pulls
     * gigabytes of terrain without choosing to.
     */
    public String distantTerrain = "ask";

    /**
     * First port to try for the loopback bridge. Lumi uses 25599 and Autopilot 25598; if this
     * one is taken the next few are tried, and the descriptor names whichever was bound.
     */
    public int bridgePort = 25597;
    public String bridgeToken = "";

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("goh");
    }

    public static synchronized ClientConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static synchronized void load() {
        ClientConfig cfg = new ClientConfig();
        Path f = dir().resolve("client.json");
        try {
            if (Files.exists(f)) {
                ClientConfig read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        ClientConfig.class);
                if (read != null) cfg = read;
            }
        } catch (Exception e) {
            GohMod.LOG.warn("could not read {}, using defaults", f, e);
        }
        if (cfg.welcomed == null) cfg.welcomed = new ArrayList<>();
        if (cfg.bridgeToken == null || cfg.bridgeToken.isBlank()) {
            byte[] b = new byte[24];
            new SecureRandom().nextBytes(b);
            cfg.bridgeToken = HexFormat.of().formatHex(b);
        }
        instance = cfg;
        save();
    }

    public static synchronized void save() {
        Path f = dir().resolve("client.json");
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (Exception e) {
            GohMod.LOG.warn("could not save {}", f, e);
        }
    }
}
