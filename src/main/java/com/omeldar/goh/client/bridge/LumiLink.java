package com.omeldar.goh.client.bridge;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.omeldar.goh.GohMod;
import com.omeldar.goh.client.ClientConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tells Lumi this mod exists, by Lumi Link v1: a descriptor file in {@code config/lumi-link/}
 * that names the loopback endpoint and its token. Lumi health-checks it and offers the tools
 * under the {@code goh_} prefix. The protocol is described in goh-autopilot's
 * docs/lumi-link.md; nothing is compiled against Lumi.
 */
public final class LumiLink {
    static final String HINT =
            "GoH is the server's own mod. It offers a shared waypoint list (imported into "
          + "JourneyMap) and distant terrain (LOD Server Support feeding Voxy). Call "
          + "goh_get_status first. Only share or remove a waypoint when the player asks for that "
          + "specific one: uploads are always the player's decision. A shared waypoint can be "
          + "overwritten or removed only by its owner or the server owner; refusals say whose it "
          + "is. Switching distant terrain on can download a lot, so do it when asked.";

    private LumiLink() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("lumi-link").resolve("goh.json");
    }

    public static void announce() {
        if (!ApiServer.running()) return;
        ClientConfig cfg = ClientConfig.get();
        try {
            JsonObject d = new JsonObject();
            d.addProperty("lumiLink", 1);
            d.addProperty("modId", "goh");
            d.addProperty("name", "GoH");
            d.addProperty("version", GohMod.version());
            d.addProperty("summary", "The GoH server's mod: shared waypoints for JourneyMap, "
                    + "distant terrain for Voxy, and what the server offers.");
            d.addProperty("prefix", "goh");
            d.addProperty("mcpUrl", "http://127.0.0.1:" + ApiServer.port() + "/mcp");
            d.addProperty("healthUrl", "http://127.0.0.1:" + ApiServer.port() + "/health");
            d.addProperty("tokenHeader", ApiServer.HEADER);
            d.addProperty("token", cfg.bridgeToken);
            d.addProperty("hint", HINT);
            JsonArray tools = new JsonArray();
            for (var el : Mcp.toolList()) tools.add(el.getAsJsonObject().get("name").getAsString());
            d.add("tools", tools);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(d),
                    StandardCharsets.UTF_8);
            GohMod.LOG.info("announced to lumi-link at {}", file());
        } catch (Exception e) {
            GohMod.LOG.warn("could not write the lumi-link descriptor", e);
        }
    }

    public static void withdraw() {
        try {
            Files.deleteIfExists(file());
        } catch (Exception ignored) {
            // A stale file only fails Lumi's health check.
        }
    }
}
