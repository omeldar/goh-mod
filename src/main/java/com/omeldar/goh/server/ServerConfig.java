package com.omeldar.goh.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.omeldar.goh.GohMod;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** {@code config/goh/server.json}: what the server says to the people who join it. */
public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ServerConfig instance;

    public String serverName = "GoH";
    /** Shown on the welcome screen, one paragraph per line. */
    public List<String> welcome = new ArrayList<>(List.of(
            "A vanilla survival server with a few quality-of-life additions.",
            "Be kind, build big, and leave the spawn area tidy."));
    public String website = "https://goh.omeldar.com/minecraft";
    /** Players without the mod get this link in chat instead of the welcome screen. */
    public String modpackUrl = "https://goh.omeldar.com/minecraft";
    public boolean waypointSharing = true;
    public int maxWaypointsPerPlayer = 50;
    public int maxWaypointsTotal = 1000;

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("goh").resolve("server.json");
    }

    public static synchronized ServerConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static synchronized void load() {
        ServerConfig cfg = new ServerConfig();
        Path f = file();
        try {
            if (Files.exists(f)) {
                ServerConfig read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        ServerConfig.class);
                if (read != null) cfg = read;
            }
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(cfg), StandardCharsets.UTF_8);
        } catch (Exception e) {
            GohMod.LOG.warn("could not read {}, using defaults", f, e);
        }
        if (cfg.welcome == null) cfg.welcome = new ArrayList<>();
        instance = cfg;
    }
}
